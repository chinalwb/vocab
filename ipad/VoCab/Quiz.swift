import Foundation

// The self-test rules, ported from SelfTest.kt (and template.html). Keep the three in step.

private func re(_ p: String) -> NSRegularExpression { try! NSRegularExpression(pattern: p) }
private func replace(_ s: String, _ r: NSRegularExpression, _ with: String = "") -> String {
    r.stringByReplacingMatches(in: s, range: NSRange(location: 0, length: (s as NSString).length), withTemplate: with)
}
private func has(_ s: String, _ r: NSRegularExpression) -> Bool {
    r.firstMatch(in: s, range: NSRange(location: 0, length: (s as NSString).length)) != nil
}

private let notWordChar = re("[^a-z0-9']")
private let leadPunct = re(#"^[^\p{L}\p{N}]+"#)
private let edgePunct = re(#"^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$"#)
private let latin = re("[A-Za-z]")
private let cjk = re("[\\u4e00-\\u9fff]")
private let boldRE = re(#"\*\*(.+?)\*\*"#)

func words(_ s: String) -> [String] { s.split(whereSeparator: \.isWhitespace).map(String.init) }

/// Case, punctuation and curly quotes are ignored when comparing words.
private func norm(_ w: String) -> String {
    replace(w.lowercased().replacingOccurrences(of: "’", with: "'").replacingOccurrences(of: "‘", with: "'"), notWordChar)
}

/// Word-level diff of my sentence against the answer, an LCS — same as diffWords() in template.html.
struct WordDiff {
    let mine: [String], answer: [String]
    let keepMine: Set<Int>, keepAnswer: Set<Int>
    let same: Bool
    var extra: Int { mine.count - keepMine.count }
    var missing: Int { answer.count - keepAnswer.count }
}

func diffWords(_ mine: String, _ answer: String) -> WordDiff {
    let a = words(mine), b = words(answer)
    let na = a.map(norm), nb = b.map(norm)
    var l = Array(repeating: Array(repeating: 0, count: b.count + 1), count: a.count + 1)
    for x in stride(from: a.count - 1, through: 0, by: -1) {
        for y in stride(from: b.count - 1, through: 0, by: -1) {
            l[x][y] = !na[x].isEmpty && na[x] == nb[y] ? l[x + 1][y + 1] + 1 : max(l[x + 1][y], l[x][y + 1])
        }
    }
    var ka = Set<Int>(), kb = Set<Int>(), x = 0, y = 0
    while x < a.count && y < b.count {
        if !na[x].isEmpty && na[x] == nb[y] { ka.insert(x); kb.insert(y); x += 1; y += 1 }
        else if l[x + 1][y] >= l[x][y + 1] { x += 1 } else { y += 1 }
    }
    return WordDiff(mine: a, answer: b, keepMine: ka, keepAnswer: kb,
                    same: na.filter { !$0.isEmpty } == nb.filter { !$0.isEmpty })
}

/// Several acceptable answers, one per line: any match wins, else the one with the most words in common.
func bestDiff(_ mine: String, _ answer: String) -> WordDiff {
    let ds = answer.split(separator: "\n").filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        .map { diffWords(mine, String($0)) }
    return ds.first { $0.same } ?? ds.max { $0.keepAnswer.count < $1.keepAnswer.count } ?? diffWords(mine, "")
}

// MARK: - the questions

/// An example that can be asked: an English sentence with a Chinese translation.
extension Example {
    var quizable: Bool { !zh.trimmingCharacters(in: .whitespaces).isEmpty && has(en, latin) && has(zh, cjk) }
}

extension Entry {
    /// The examples this entry is quizzed on, in note order.
    var quizExamples: [Example] {
        blocks.flatMap { b -> [Example] in if case .examples(let xs) = b { return xs } else { return [] } }.filter(\.quizable)
    }

    /// A 自测题's answer: the quote under **答案**.
    var selfTestAnswer: String {
        var want = false
        for b in blocks {
            if case .para(let t) = b, t.hasPrefix("**答案") { want = true }
            else if case .quote(let lines) = b, want { return lines.joined(separator: "\n") }
        }
        return ""
    }

    /// The questions that must each be answered right to move on: plain English, or "" for a 自测题.
    var questionKeys: [String] { level == "SELFTEST" ? [""] : quizExamples.map { plain($0.en) } }

    /// What 🔊 next to the title reads: the headword, or a 自测题's first answer.
    var speakText: String {
        if level == "SELFTEST" { return selfTestAnswer.split(separator: "\n").first.map(String.init) ?? "" }
        let t = title.range(of: #"^(.+?)\s*[(（].+[)）]$"#, options: .regularExpression).map { _ in
            String(title.prefix { $0 != "(" && $0 != "（" }).trimmingCharacters(in: .whitespaces)
        } ?? title
        return has(t, latin) ? t : ""
    }
}

/// One question: the English answer (plain and as written, its key part in **bold**) and the Chinese prompt.
struct Question: Equatable {
    let en: String, zh: String, raw: String
    /// Its place in note order, and how many there are.
    let k: Int, of: Int
}

/// A piece of a quiz sentence: a **bold** key part to type, or the text around it.
struct Blank: Hashable { let key: Bool; let text: String }

/// 打字 asks only for the key part (用户 2026-10-19). Nil when nothing is bold: typed whole.
func blanksOf(_ raw: String) -> [Blank]? {
    guard has(raw, boldRE) else { return nil }
    return raw.components(separatedBy: "**").enumerated()
        .map { Blank(key: $0.offset % 2 == 1, text: plain($0.element)) }
        .filter { $0.key || !$0.text.isEmpty }
}

/// The filled-in sentence as a whole: [fills] by key part, in order.
func composeBlanks(_ parts: [Blank], _ fills: [String]) -> String {
    var k = 0
    let s = parts.map { p -> String in
        guard p.key else { return p.text }
        defer { k += 1 }
        return k < fills.count ? fills[k].trimmingCharacters(in: .whitespaces) : ""
    }.joined()
    return words(s).joined(separator: " ")
}

/// 打乱: a random order of 0..<n that doesn't start with [first], so a tap visibly changes the question.
func dealOrder(_ n: Int, first: Int) -> [Int] {
    var o = Array(0..<n).shuffled()
    if n > 1 && o[0] == first { o.swapAt(0, 1) }
    return o
}

// MARK: - 选词

/// A sentence as chips: a word keeps the punctuation after it, loses any before it.
func chipWords(_ s: String) -> [String] { words(s).map { replace($0, leadPunct) }.filter { !$0.isEmpty } }

/// A chip without its punctuation: what strays and duplicates are compared by.
func bareWord(_ w: String) -> String { replace(w, edgePunct).lowercased() }

/// A seeded generator, so a sentence's chips come out the same each time it's shown.
struct SeededRNG: RandomNumberGenerator {
    var state: UInt64
    init(_ seed: Int) { state = UInt64(bitPattern: Int64(seed)) &+ 0x9E37_79B9_7F4A_7C15 }
    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}

private let strayPool = ["the", "a", "an", "to", "of", "in", "on", "at", "for", "with", "is", "are", "was", "has", "that", "it", "and", "but"]

private func distinct(_ xs: [String], by key: (String) -> String) -> [String] {
    var seen = Set<String>()
    return xs.filter { seen.insert(key($0)).inserted }
}

/// A few words that aren't in [answer]: from the entry's other sentences first, then stock function words.
func strayWords(_ answer: String, others: [String], seed: Int, n: Int = 6) -> [String] {
    let have = Set(chipWords(answer).map(bareWord))
    var rng = SeededRNG(seed)
    // strays go in bare, so a "." can't mark them as a sentence end
    let own = distinct(others.flatMap(chipWords).map { replace($0, edgePunct) }.filter { !have.contains(bareWord($0)) }, by: bareWord)
        .shuffled(using: &rng)
    let stock = strayPool.filter { !have.contains($0) }.shuffled(using: &rng)
    return Array(distinct(own + stock) { $0.lowercased() }.prefix(n))
}

/// 选词, four at a time: after k picks, the answer's word k+1, two later words and a stray. A word still
/// there from the last set keeps its slot. Same as WordBankState in SelfTest.kt.
struct WordBank {
    let words: [String]
    let strays: [String]
    let seed: Int
    static let chips = 4

    init(answer: String, strays: [String], seed: Int) {
        words = chipWords(answer); self.strays = strays; self.seed = seed
    }

    private func optionsAt(_ k: Int) -> [String] {
        guard k < words.count else { return [] }
        var rng = SeededRNG(seed &* 31 &+ k)
        let right = words[k]
        var seen: Set<String> = [bareWord(right)]
        let later = Array(words.dropFirst(k + 1).shuffled(using: &rng).filter { seen.insert(bareWord($0)).inserted }.prefix(2))
        let fill = Array(strays.shuffled(using: &rng).filter { seen.insert(bareWord($0)).inserted }.prefix(Self.chips - 1 - later.count))
        return ([right] + later + fill).shuffled(using: &rng)
    }

    func layout(after k: Int) -> [String] {
        var prev: [String] = []
        for j in 0...k {
            let opts = optionsAt(j)
            var out = [String?](repeating: nil, count: opts.count)
            var rest: [String] = []
            for w in opts {
                if let at = prev.firstIndex(where: { bareWord($0) == bareWord(w) }), at < out.count, out[at] == nil { out[at] = w }
                else { rest.append(w) }
            }
            prev = out.map { $0 ?? rest.removeFirst() }
        }
        return prev
    }
}

// MARK: - moving on

/// Every one of [questions] answered right since [since] — what earns the move to the next stage.
func allCorrect(_ attempts: [Attempt], since: Int64, questions: [String]) -> Bool {
    !questions.isEmpty && questions.allSatisfy { q in attempts.contains { $0.t >= since && $0.ok && $0.ex == q } }
}

/// How many questions are still to be answered right in this stage; nil with nothing to check against.
func remaining(_ attempts: [Attempt], since: Int64, questions: [String]) -> Int? {
    questions.isEmpty ? nil : questions.filter { q in !attempts.contains { $0.t >= since && $0.ok && $0.ex == q } }.count
}
