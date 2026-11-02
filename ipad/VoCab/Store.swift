import Foundation
import Observation

/// An entry's lifecycle: 学习中 → 复习 → 考核 → 已掌握. [rawValue] is what stage.json / vk.stage store.
enum Stage: String, CaseIterable, Codable, Identifiable {
    case learn, review, test, done
    var id: String { rawValue }
    var label: String { ["学习中", "复习中", "考核中", "已掌握"][index] }
    var short: String { ["学习", "复习", "考核", "掌握"][index] }
    var index: Int { Stage.allCases.firstIndex(of: self)! }
    var next: Stage? { index + 1 < Stage.allCases.count ? Stage.allCases[index + 1] : nil }
}

/// One reveal of a question; a blank [text] is a 偷看. [ex] is the sentence asked (plain English), "" for a 自测题.
/// The same record as vk.selftest on the page and selftest.json in the Android app.
struct Attempt: Codable, Hashable {
    let t: Int64
    let text: String
    var ok: Bool
    /// "entry" (the entry page), "review" (复习), "test" (考核)
    let via: String
    var ex: String = ""
}

/// stage.json: the moves, and when each was made (ms), so a later 同步 can let the newest win.
struct StageData: Codable {
    var stages: [String: String] = [:]
    var t: [String: Int64] = [:]
}

let maxAttemptsPerEntry = 50
func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

/// The whole app's state: the notes, where each entry is, and the self-test log. Saved as JSON in
/// Application Support, in the same shapes the Android app uses, ready for 同步 to progress-taobao.json.
@MainActor @Observable
final class AppModel {
    /// taobao's notes on Pages; the bundled data.json is the fallback.
    static let site = URL(string: "https://chinalwb.github.io/vocab/taobao/")!

    private(set) var entries: [Entry] = []
    private(set) var stageData = StageData()
    private(set) var attempts: [String: [Attempt]] = [:]
    private(set) var dataHash = ""
    private(set) var review = ReviewData()
    private(set) var history = HistoryData()
    private(set) var quizOut: QuizOut = [:]
    var updateNote: String?
    /// The 复习 / 考核 run on screen, if any.
    var run: RunSession?

    // 同步 (Sync.swift)
    var sync = SyncStatus()
    @ObservationIgnored var pending: Task<Void, Never>?
    @ObservationIgnored var syncAgain = false

    private let dir: URL = {
        let d = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }()
    private var dataFile: URL { dir.appendingPathComponent("data.json") }

    init() {
        let cached = (try? Data(contentsOf: dataFile)).flatMap(decodeData)
        let bundled = Bundle.main.url(forResource: "data", withExtension: "json").flatMap { try? Data(contentsOf: $0) }.flatMap(decodeData)
        // the newer of the two: a new install's bundle can be newer than an old download
        if let d = [cached, bundled].compactMap({ $0 }).max(by: { $0.generated < $1.generated }) { use(d.data) }
        stageData = load("stage.json") ?? StageData()
        attempts = load("selftest.json") ?? [:]
        review = load("review.json") ?? ReviewData()
        history = load("history.json") ?? HistoryData()
        quizOut = load("quizout.json") ?? [:]
        sync = SyncStatus.saved()
    }

    private struct Loaded { let data: VocabData; let generated: String }
    private func decodeData(_ raw: Data) -> Loaded? {
        struct Gen: Decodable { let generated: String? }
        guard let d = try? JSONDecoder().decode(VocabData.self, from: raw), d.schema <= supportedSchema else { return nil }
        return Loaded(data: d, generated: (try? JSONDecoder().decode(Gen.self, from: raw))?.generated ?? "")
    }
    private func use(_ d: VocabData) { entries = d.entries; dataHash = d.hash }

    func load<T: Decodable>(_ name: String) -> T? {
        (try? Data(contentsOf: dir.appendingPathComponent(name))).flatMap { try? JSONDecoder().decode(T.self, from: $0) }
    }
    func save<T: Encodable>(_ v: T, _ name: String) {
        try? JSONEncoder().encode(v).write(to: dir.appendingPathComponent(name), options: .atomic)
    }

    /// Polls meta.json; downloads data.json only when its hash changed.
    func refresh() async {
        do {
            let (m, _) = try await URLSession.shared.data(from: Self.site.appendingPathComponent("meta.json"))
            let meta = try JSONDecoder().decode(Meta.self, from: m)
            guard meta.hash != dataHash, meta.schema <= supportedSchema else { return }
            let (raw, _) = try await URLSession.shared.data(from: Self.site.appendingPathComponent("data.json"))
            guard let d = decodeData(raw) else { return }
            let before = Set(entries.map { "\($0.anchor)\u{1F}\($0.hash)" })
            let changed = d.data.entries.filter { !before.contains("\($0.anchor)\u{1F}\($0.hash)") }.count
            try raw.write(to: dataFile, options: .atomic)
            use(d.data)
            if changed > 0 { updateNote = "笔记更新了:\(changed) 条新增或修改" }
        } catch {
            // offline or not published yet: keep what we have
        }
    }

    func entry(_ anchor: String) -> Entry? { entries.first { $0.anchor == anchor } }

    // MARK: stages

    /// Only moves are stored: a 自测题 starts in 考核, everything else in 学习中; an essay has no stage.
    func stage(_ e: Entry) -> Stage? {
        if e.isEssay { return nil }
        return stageData.stages[e.anchor].flatMap(Stage.init) ?? (e.level == "SELFTEST" ? .test : .learn)
    }
    /// When the entry entered its stage (0 if it never moved).
    func since(_ e: Entry) -> Int64 { stageData.t[e.anchor] ?? 0 }

    func move(_ e: Entry, to s: Stage) {
        stageData.stages[e.anchor] = s.rawValue
        stageData.t[e.anchor] = nowMs()
        save(stageData, "stage.json")
        changed()
    }

    // MARK: self-test log

    func attempts(_ e: Entry) -> [Attempt] { attempts[e.anchor] ?? [] }

    func record(_ e: Entry, _ a: Attempt) {
        attempts[e.anchor] = Array((attempts(e) + [a]).suffix(maxAttemptsPerEntry))
        save(attempts, "selftest.json")
        changed()
    }

    /// 算我对: the last attempt becomes right.
    func markLastRight(_ e: Entry) {
        guard var list = attempts[e.anchor], !list.isEmpty else { return }
        list[list.count - 1].ok = true
        attempts[e.anchor] = list
        save(attempts, "selftest.json")
        changed()
    }

    /// This sentence was answered right before (any time): ① 选词 is skipped.
    func rightBefore(_ e: Entry, _ ex: String) -> Bool { attempts(e).contains { $0.ok && $0.ex == ex } }

    /// Every question right since the entry entered its stage — the move is offered (or, from 学习中, made).
    /// 复习 → 考核 also needs promoteReps right in a row on the SM-2 card; then a 复习 run moves it by
    /// itself, and an entry graded by me qualifies on that alone. Same as advanceReady() on the page.
    func advanceReady(_ e: Entry) -> Bool {
        guard let s = stage(e), s.next != nil else { return false }
        let qs = questions(e)
        let all = qs.allSatisfy { q in attempts(e).contains { $0.t >= since(e) && $0.ok && $0.ex == q } }
        if s == .review { return reps(e) >= promoteReps && all }
        return !qs.isEmpty && all
    }

    // MARK: 不再考这句

    /// The examples asked, minus the ones left out on the page or the phone — unless that's all of them.
    func examples(_ e: Entry) -> [Example] {
        let xs = e.quizExamples
        guard let out = quizOut[e.anchor] else { return xs }
        let kept = xs.filter { out[plain($0.en)]?.on != true }
        return kept.isEmpty ? xs : kept
    }
    /// What must each be answered right to move on: plain English, or "" for a 自测题.
    func questions(_ e: Entry) -> [String] { e.level == "SELFTEST" ? [""] : examples(e).map { plain($0.en) } }

    // MARK: 复习's SM-2 cards

    func card(_ e: Entry) -> Card? { review.cards[e.anchor] }
    func reps(_ e: Entry) -> Int { card(e)?.reps ?? 0 }
    /// Due today, or new to 复习 (no card yet).
    func reviewDue(_ e: Entry) -> Bool { card(e).map { $0.due <= dayNo() } ?? true }

    func grade(_ e: Entry, ok: Bool) {
        var c = schedule(card(e) ?? Card(), q: ok ? 4 : 1, today: dayNo())
        c.t = nowMs()
        review.cards[e.anchor] = c
        save(review, "review.json")
        changed()
    }
    /// 算我对 on a run's last sentence: back to the card before it was graded, to be graded again.
    func restoreCard(_ e: Entry, _ c: Card?) {
        review.cards[e.anchor] = c
        save(review, "review.json")
    }

    // MARK: the stage lists

    func inStage(_ s: Stage) -> [Entry] {
        entries.filter { stage($0) == s }.sorted {
            let a = $0.date.replacingOccurrences(of: "~", with: ""), b = $1.date.replacingOccurrences(of: "~", with: "")
            return a != b ? a > b : ($0.no ?? 0) > ($1.no ?? 0)
        }
    }

    /// 复习 today: what's due (soonest first), then what's new to it — 会写 first, then most recently 收录.
    func reviewPlan() -> (due: [Entry], fresh: [Entry]) {
        let live = entries.filter { stage($0) == .review }, today = dayNo()
        let due = live.filter { (card($0)?.due ?? .max) <= today }.sorted { card($0)!.due < card($1)!.due }
        let fresh = live.filter { card($0) == nil }.sorted {
            ($0.mastery == "write") != ($1.mastery == "write") ? $0.mastery == "write"
                : $0.date.replacingOccurrences(of: "~", with: "") > $1.date.replacingOccurrences(of: "~", with: "")
        }
        return (due, fresh)
    }

    /// 0 never tried, 1 wrong or peeked last time, 2 right last time — how 考核 groups and orders.
    func lastGroup(_ e: Entry) -> Int {
        guard let l = attempts(e).last else { return 0 }
        return l.text.isEmpty || !l.ok ? 1 : 2
    }

    /// The run's queue: 复习 today's plan; 考核 never tried, then wrong last time, then the oldest try.
    func quizQueue(_ s: Stage) -> [Entry] {
        if s == .review { let p = reviewPlan(); return p.due + p.fresh }
        typealias Ranked = (e: Entry, g: Int, t: Int64)
        let ranked: [Ranked] = inStage(s).map { e in (e, lastGroup(e), attempts(e).last?.t ?? Int64(e.no ?? 0)) }
        return ranked.sorted { a, b in a.g != b.g ? a.g < b.g : a.t < b.t }.map(\.e)
    }

    /// The sentences a run asks of an entry (up to runSentenceMax), as plain English in note order: first
    /// the ones not yet right in this stage, then the ones last answered wrong, then the longest unasked.
    func runSentences(_ e: Entry) -> [String] {
        let xs = examples(e).map { plain($0.en) }, log = attempts(e), since = since(e)
        func rank(_ k: Int) -> (g: Int, t: Int64, k: Int) {
            let mine = log.filter { $0.ex == xs[k] }
            let last = mine.last
            let rightHere = mine.contains { $0.t >= since && $0.ok }
            let wrongLast = last.map { $0.text.isEmpty || !$0.ok } ?? false
            return (!rightHere ? 0 : wrongLast ? 1 : 2, last?.t ?? 0, k)
        }
        let ranked: [(g: Int, t: Int64, k: Int)] = xs.indices.map(rank)
        return ranked.sorted { a, b in a.g != b.g ? a.g < b.g : a.t != b.t ? a.t < b.t : a.k < b.k }
            .prefix(runSentenceMax).map(\.k).sorted().map { xs[$0] }
    }

    // MARK: 学习历史

    /// Opened an entry, or met it in a run ([via] "entry" / "review" / "test").
    func visit(_ e: Entry, via: String) {
        history.visits = trimHistory([Visit(a: e.anchor, t: nowMs(), via: via)] + history.visits, clearAt: history.clearAt)
        save(history, "history.json")
        changed()
    }

    // MARK: 同步's view of the stores (Sync.swift)

    func replaceSynced(stages: StageData, attempts at: [String: [Attempt]], review r: ReviewData, history h: HistoryData, quizOut q: QuizOut) {
        stageData = stages; attempts = at; review = r; history = h; quizOut = q
        save(stageData, "stage.json"); save(attempts, "selftest.json"); save(review, "review.json")
        save(history, "history.json"); save(quizOut, "quizout.json")
    }
}
