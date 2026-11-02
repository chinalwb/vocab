import Foundation
import Observation

// 复习's schedule: SM-2, same rules as review/ReviewStore.kt and template.html. The quiz grades it —
// an entry answered right is 记得 (4), wrong is 忘了 (1) — so there are no grade buttons.

/// SM-2 state for one entry. [due] is a day number (dayNo()); [t] when it was last graded (ms),
/// so 同步 lets the newest win.
struct Card: Codable, Equatable {
    var ease = 2.5
    var interval = 0
    var reps = 0
    var lapses = 0
    var due: Int64 = 0
    var t: Int64 = 0

    init() {}
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        ease = try c.decodeIfPresent(Double.self, forKey: .ease) ?? 2.5
        interval = try c.decodeIfPresent(Int.self, forKey: .interval) ?? 0
        reps = try c.decodeIfPresent(Int.self, forKey: .reps) ?? 0
        lapses = try c.decodeIfPresent(Int.self, forKey: .lapses) ?? 0
        due = try c.decodeIfPresent(Int64.self, forKey: .due) ?? 0
        t = try c.decodeIfPresent(Int64.self, forKey: .t) ?? 0
    }
}

/// review.json: the cards, and the fields progress.json carries with them.
struct ReviewData: Codable {
    var cards: [String: Card] = [:]
    var newDay: Int64 = 0
    var newToday = 0
    /// 重置复习进度 (ms): 同步 drops cards graded before it on every device.
    var resetAt: Int64 = 0
}

/// 复习 → 考核 by itself: right answers in a row on the SM-2 card. Right on days 0, +1 and +3 = still
/// remembered after a three-day gap. Same as PROMOTE_REPS in ReviewStore.kt / template.html.
let promoteReps = 3
/// A 复习 / 考核 run asks up to this many of an entry's sentences (RUN_SENTENCES).
let runSentenceMax = 3

/// Today as days since 1970-01-01 in local time — LocalDate.toEpochDay() / the page's dayNo().
func dayNo(_ d: Date = Date()) -> Int64 {
    let c = Calendar.current.dateComponents([.year, .month, .day], from: d)
    var utc = Calendar(identifier: .gregorian)
    utc.timeZone = TimeZone(identifier: "UTC")!
    return Int64((utc.date(from: c)!.timeIntervalSince1970 / 86400).rounded(.down))
}

func schedule(_ c: Card, q: Int, today: Int64) -> Card {
    var n = c
    n.ease = max(1.3, c.ease + (0.1 - Double(5 - q) * (0.08 + Double(5 - q) * 0.02)))
    if q < 3 {
        n.interval = 1; n.reps = 0; n.lapses = c.lapses + 1; n.due = today + 1
    } else {
        n.reps = c.reps + 1
        n.interval = n.reps == 1 ? 1 : n.reps == 2 ? (q == 5 ? 4 : 3) : max(c.interval + 1, Int((Double(c.interval) * n.ease).rounded()))
        n.due = today + Int64(n.interval)
    }
    return n
}

/// 学习历史: one visit per entry, newest first. Same as Visit in HistoryStore.kt / the page's hist.
struct Visit: Codable, Equatable { let a: String; let t: Int64; let via: String }
struct HistoryData: Codable { var visits: [Visit] = []; var clearAt: Int64 = 0 }
let historyMax = 100

func trimHistory(_ xs: [Visit], clearAt: Int64) -> [Visit] {
    var seen = Set<String>()
    return Array(xs.filter { $0.t > clearAt }.sorted { $0.t > $1.t }.filter { seen.insert($0.a).inserted }.prefix(historyMax))
}

/// 不再考这句 (set on the page or the phone; read here): {anchor: {plain English: Flag}}.
struct Flag: Codable, Equatable { var on: Bool; var t: Int64 }
typealias QuizOut = [String: [String: Flag]]

/// One 复习 / 考核 run: the queue, where it's at, and what it moved. Same fields as newRun() on the page
/// and QuizSession in the Android app.
@MainActor @Observable
final class RunSession: Identifiable {
    let id = UUID()
    let stage: Stage
    let queue: [String]
    var index = 0
    /// 复习: the notes were read (先看一遍 → 翻面做题)
    var studied: Bool
    /// the sentences asked of this entry (plain English), and the one on now
    var qs: [String]?
    var k = 0
    /// bumps when a sentence starts over (打乱), so the sheet starts fresh
    var round = 0
    var shuffle = false
    /// every sentence of this entry right so far; decides the grade and the moves, once, after the last
    var entryOk = true
    var missed = false
    /// for 算我对 on the last sentence: the result and the card before it was graded
    var entryOkBefore = true
    var cardBefore: Card?
    /// an entry with nothing to check against: how I graded it
    var graded: Bool?
    var promoted: [String] = []
    var demoted: [String] = []
    var done = 0
    var ok = 0
    var checked = 0
    var advanced = 0

    init(stage: Stage, queue: [String], studied: Bool = false) {
        self.stage = stage; self.queue = queue; self.studied = studied
    }

    var anchor: String? { index < queue.count ? queue[index] : nil }

    /// On to the next entry of the queue.
    func nextEntry() {
        index += 1; done += 1
        studied = false; qs = nil; k = 0; round += 1
        entryOk = true; missed = false; graded = nil; cardBefore = nil
    }
}
