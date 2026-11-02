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
    var updateNote: String?

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
    }

    private struct Loaded { let data: VocabData; let generated: String }
    private func decodeData(_ raw: Data) -> Loaded? {
        struct Gen: Decodable { let generated: String? }
        guard let d = try? JSONDecoder().decode(VocabData.self, from: raw), d.schema <= supportedSchema else { return nil }
        return Loaded(data: d, generated: (try? JSONDecoder().decode(Gen.self, from: raw))?.generated ?? "")
    }
    private func use(_ d: VocabData) { entries = d.entries; dataHash = d.hash }

    private func load<T: Decodable>(_ name: String) -> T? {
        (try? Data(contentsOf: dir.appendingPathComponent(name))).flatMap { try? JSONDecoder().decode(T.self, from: $0) }
    }
    private func save<T: Encodable>(_ v: T, _ name: String) {
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
    }

    // MARK: self-test log

    func attempts(_ e: Entry) -> [Attempt] { attempts[e.anchor] ?? [] }

    func record(_ e: Entry, _ a: Attempt) {
        attempts[e.anchor] = Array((attempts(e) + [a]).suffix(maxAttemptsPerEntry))
        save(attempts, "selftest.json")
    }

    /// 算我对: the last attempt becomes right.
    func markLastRight(_ e: Entry) {
        guard var list = attempts[e.anchor], !list.isEmpty else { return }
        list[list.count - 1].ok = true
        attempts[e.anchor] = list
        save(attempts, "selftest.json")
    }

    /// This sentence was answered right before (any time): ① 选词 is skipped.
    func rightBefore(_ e: Entry, _ ex: String) -> Bool { attempts(e).contains { $0.ok && $0.ex == ex } }

    /// Every question right since the entry entered 学习中 / 考核 — the move is offered (or, from 学习中, made).
    /// 复习 → 考核 also needs SM-2 (复习 runs), which isn't ported yet, so it's only offered by hand.
    func advanceReady(_ e: Entry) -> Bool {
        guard let s = stage(e), s.next != nil, s != .review else { return false }
        return allCorrect(attempts(e), since: since(e), questions: e.questionKeys)
    }
}
