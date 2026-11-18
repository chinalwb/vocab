import Foundation

/// The same authored questions as practices.json, kept apart from normal example quizzes.
struct SpecialPracticeSet: Decodable, Hashable {
    let anchor: String
    let title: String
    let date: String
    let description: String
    let skills: [String: String]
    let questions: [SpecialPracticeQuestion]

    var orderedSkills: [String] {
        questions.reduce(into: [String]()) { if !$0.contains($1.skill) { $0.append($1.skill) } }
    }
}

struct SpecialPracticeQuestion: Decodable, Identifiable, Hashable {
    enum Kind: String, Decodable { case fill, correct, rewrite
        var label: String { switch self { case .fill: return "填空"; case .correct: return "改错"; case .rewrite: return "按要求改写" } }
    }
    let id: String
    let skill: String
    let type: Kind
    let prompt: String
    let hint: String
    let answers: [String]
    let explanation: String
    let note: String

    func accepts(_ answer: String) -> Bool {
        let text = normalizedPracticeAnswer(answer)
        return !text.isEmpty && answers.contains { normalizedPracticeAnswer($0) == text }
    }
}

/// Match the web's accepted contractions, capitalization and punctuation without losing tense/subject.
func normalizedPracticeAnswer(_ answer: String) -> String {
    var text = answer.precomposedStringWithCompatibilityMapping.lowercased()
        .replacingOccurrences(of: "’", with: "'").replacingOccurrences(of: "‘", with: "'")
    let contractions = ["don": "do not", "doesn": "does not", "didn": "did not", "isn": "is not",
                        "aren": "are not", "wasn": "was not", "weren": "were not", "couldn": "could not",
                        "shouldn": "should not", "haven": "have not", "hasn": "has not", "hadn": "had not",
                        "mustn": "must not", "wouldn": "would not"]
    for (stem, full) in contractions {
        text = text.replacingOccurrences(of: "\\b\(stem)'?t\\b", with: full, options: .regularExpression)
    }
    return text.replacingOccurrences(of: #"\b(can'?t|cannot)\b"#, with: "can not", options: .regularExpression)
        .replacingOccurrences(of: #"[^\p{L}\p{N}\s]"#, with: " ", options: .regularExpression)
        .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
        .trimmingCharacters(in: .whitespacesAndNewlines)
}

enum SpecialPracticeMode: String, Codable {
    case focused, mixed, review
    init(from decoder: Decoder) throws { self = Self(rawValue: try decoder.singleValueContainer().decode(String.self)) ?? .mixed }
    var label: String { switch self { case .focused: return "分项"; case .mixed: return "混合"; case .review: return "重练" } }
}

struct SpecialPracticeAttempt: Codable, Identifiable, Hashable {
    let id: String
    let q: String
    let run: String
    let t: Int64
    let answer: String
    let ok: Bool
    let help: Bool
}

struct SpecialPracticeRun: Codable, Identifiable {
    let id: String
    let mode: SpecialPracticeMode
    let skill: String
    let order: [String]
    var index = 0
    var drafts: [String: String] = [:]
    var help: [String: Bool] = [:]
    var submissions: [String: String] = [:]
    var completed = false
    let started: Int64
    var t: Int64

    var questionID: String? { order.indices.contains(index) ? order[index] : nil }
}

/// The web's shape (practice.js spRun()): `status` "active" / "completed". The first iPad builds wrote
/// `completed`; both still read. Written the same way on both sides, so 同步 compares equal.
extension SpecialPracticeRun {
    private enum Key: String, CodingKey { case id, mode, skill, order, index, drafts, help, submissions, status, completed, started, t }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Key.self)
        id = try c.decode(String.self, forKey: .id)
        mode = (try? c.decode(SpecialPracticeMode.self, forKey: .mode)) ?? .mixed
        skill = try c.decodeIfPresent(String.self, forKey: .skill) ?? ""
        order = try c.decodeIfPresent([String].self, forKey: .order) ?? []
        index = try c.decodeIfPresent(Int.self, forKey: .index) ?? 0
        drafts = try c.decodeIfPresent([String: String].self, forKey: .drafts) ?? [:]
        help = try c.decodeIfPresent([String: Bool].self, forKey: .help) ?? [:]
        submissions = try c.decodeIfPresent([String: String].self, forKey: .submissions) ?? [:]
        if let status = try c.decodeIfPresent(String.self, forKey: .status) { completed = status == "completed" }
        else { completed = try c.decodeIfPresent(Bool.self, forKey: .completed) ?? false }
        started = try c.decodeIfPresent(Int64.self, forKey: .started) ?? 0
        t = try c.decodeIfPresent(Int64.self, forKey: .t) ?? 0
    }
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: Key.self)
        try c.encode(id, forKey: .id); try c.encode(mode, forKey: .mode); try c.encode(skill, forKey: .skill)
        try c.encode(order, forKey: .order); try c.encode(index, forKey: .index); try c.encode(drafts, forKey: .drafts)
        try c.encode(help, forKey: .help); try c.encode(submissions, forKey: .submissions)
        try c.encode(completed ? "completed" : "active", forKey: .status)
        try c.encode(started, forKey: .started); try c.encode(t, forKey: .t)
    }
}

struct SpecialPracticeSummary {
    let done: Int
    let firstRight: Int
    let review: [String]
}

/// Saved in special-practice.json, apart from selftest.json and stage.json; 同步 carries it whole in
/// progress-taobao.json's specialPractice, answers included (用户 2026-11-18) — see merge(_:).
struct SpecialPracticeRecord: Codable {
    var runs: [String: SpecialPracticeRun] = [:]
    var current = ""
    var attempts: [SpecialPracticeAttempt] = []
    var currentRun: SpecialPracticeRun? { runs[current] }

    func summary(_ set: SpecialPracticeSet) -> SpecialPracticeSummary {
        let ids = Set(set.questions.map(\.id))
        var first: [String: SpecialPracticeAttempt] = [:], last: [String: SpecialPracticeAttempt] = [:]
        for a in attempts where ids.contains(a.q) {
            if first[a.q] == nil { first[a.q] = a }
            last[a.q] = a
        }
        return SpecialPracticeSummary(done: last.count, firstRight: first.values.filter { $0.ok && !$0.help }.count,
            review: set.questions.filter { q in last[q.id].map { !$0.ok || $0.help } ?? false }.map(\.id))
    }

    @discardableResult mutating func start(_ set: SpecialPracticeSet, mode: SpecialPracticeMode, skill: String = "") -> Bool {
        let review = Set(summary(set).review)
        var questions = set.questions.filter { mode == .review ? review.contains($0.id) : skill.isEmpty || $0.skill == skill }
        if mode == .focused {
            let positions = Dictionary(uniqueKeysWithValues: set.orderedSkills.enumerated().map { ($0.element, $0.offset) })
            questions = questions.enumerated().sorted {
                let a = positions[$0.element.skill] ?? 0, b = positions[$1.element.skill] ?? 0
                return a == b ? $0.offset < $1.offset : a < b
            }.map(\.element)
        }
        if mode == .mixed { questions.shuffle() }
        guard !questions.isEmpty else { return false }
        let id = UUID().uuidString, t = Self.time()
        runs[id] = SpecialPracticeRun(id: id, mode: mode, skill: skill, order: questions.map(\.id), started: t, t: t)
        current = id
        return true
    }

    mutating func saveDraft(_ answer: String) {
        guard var run = currentRun, let id = run.questionID, run.submissions[id] == nil else { return }
        run.drafts[id] = answer; run.t = Self.time(); runs[current] = run
    }
    mutating func hint() {
        guard var run = currentRun, let id = run.questionID, run.submissions[id] == nil else { return }
        run.help[id] = true; run.t = Self.time(); runs[current] = run
    }
    @discardableResult mutating func submit(_ set: SpecialPracticeSet) -> Bool {
        guard var run = currentRun, !run.completed, let id = run.questionID, run.submissions[id] == nil,
              let q = set.questions.first(where: { $0.id == id }) else { return false }
        let answer = (run.drafts[id] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !answer.isEmpty else { return false }
        let t = Self.time(), attempt = SpecialPracticeAttempt(id: UUID().uuidString, q: id, run: run.id, t: t,
            answer: answer, ok: q.accepts(answer), help: run.help[id] == true)
        attempts.append(attempt); run.submissions[id] = attempt.id; run.t = t; runs[current] = run
        return true
    }
    mutating func next() {
        guard var run = currentRun, let id = run.questionID, run.submissions[id] != nil else { return }
        if run.index == run.order.count - 1 { run.completed = true } else { run.index += 1 }
        run.t = Self.time(); runs[current] = run
    }
    func attempt(_ run: SpecialPracticeRun, question id: String) -> SpecialPracticeAttempt? {
        attempts.first { $0.id == run.submissions[id] }
    }
    /// Back to an earlier run. An unfinished one changed last now, so 同步 makes it current on every
    /// device; looking at a finished one's results doesn't take that over (like the page's resume).
    mutating func resume(_ id: String) {
        guard var run = runs[id] else { return }
        if !run.completed { run.t = Self.time(); runs[id] = run }
        current = id
    }

    // 同步: the same rules as specialMerge() / specialOut() in practice.js, so both sides agree.
    /// Attempts are a union by id; a run, newest t wins; current is whichever side's current run changed last.
    mutating func merge(_ o: SpecialPracticeRecord) {
        let mine = current
        for (id, x) in o.runs where x.t > (runs[id]?.t ?? .min) { runs[id] = x }
        var seen = Set(attempts.map(\.id))
        for a in o.attempts where !seen.contains(a.id) { attempts.append(a); seen.insert(a.id) }
        attempts.sort { Self.before(($0.t, $0.id), ($1.t, $1.id)) }
        current = [mine, o.current].compactMap { runs[$0] }.max { Self.before(($0.t, $0.id), ($1.t, $1.id)) }?.id ?? ""
    }
    /// What goes up: attempts in time order, no current pointing nowhere; nil when it was only looked at.
    var synced: SpecialPracticeRecord? {
        if runs.isEmpty && attempts.isEmpty { return nil }
        var r = self
        r.attempts.sort { Self.before(($0.t, $0.id), ($1.t, $1.id)) }
        if r.runs[r.current] == nil { r.current = "" }
        return r
    }
    private static func before(_ a: (Int64, String), _ b: (Int64, String)) -> Bool { a.0 != b.0 ? a.0 < b.0 : a.1 < b.1 }
    private static func time() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}

extension SpecialPracticeRecord {
    private enum Key: String, CodingKey { case runs, current, attempts }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Key.self)
        runs = try c.decodeIfPresent([String: SpecialPracticeRun].self, forKey: .runs) ?? [:]
        current = try c.decodeIfPresent(String.self, forKey: .current) ?? ""
        attempts = try c.decodeIfPresent([SpecialPracticeAttempt].self, forKey: .attempts) ?? []
    }
}
