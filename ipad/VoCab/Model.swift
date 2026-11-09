import Foundation

/// The data.json schema this app understands; must match SCHEMA in build.py.
let supportedSchema = 1

struct Meta: Decodable { let schema: Int; let hash: String }

struct VocabData: Decodable {
    let schema: Int
    let hash: String
    let entries: [Entry]
}

/// One note. [level] is SENTENCE, COLLOC, ESSAY, A1–C2… — see detect_level in build.py.
struct Entry: Decodable, Identifiable, Hashable {
    let anchor: String
    /// Its number in the ## 目录 list; nil for an essay (no #n).
    let no: Int?
    let title: String
    let level: String
    let ipa: String
    let pos: String
    let cefr: String
    let date: String
    let mastery: String
    let gloss: String
    let blocks: [Block]
    let hash: String

    var id: String { anchor }
    var isEssay: Bool { level == "ESSAY" }

    enum CodingKeys: String, CodingKey { case anchor, no, title, level, ipa, pos, cefr, date, mastery, gloss, blocks, hash }

    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        anchor = try c.decode(String.self, forKey: .anchor)
        no = try c.decodeIfPresent(Int.self, forKey: .no)
        title = try c.decode(String.self, forKey: .title)
        level = try c.decode(String.self, forKey: .level)
        ipa = try c.decodeIfPresent(String.self, forKey: .ipa) ?? ""
        pos = try c.decodeIfPresent(String.self, forKey: .pos) ?? ""
        cefr = try c.decodeIfPresent(String.self, forKey: .cefr) ?? ""
        date = try c.decodeIfPresent(String.self, forKey: .date) ?? ""
        mastery = try c.decodeIfPresent(String.self, forKey: .mastery) ?? "read"
        gloss = try c.decodeIfPresent(String.self, forKey: .gloss) ?? ""
        blocks = try c.decodeIfPresent([Block].self, forKey: .blocks) ?? []
        hash = try c.decodeIfPresent(String.self, forKey: .hash) ?? ""
        sections = splitSections(blocks)
        searchText = ([title, anchor, gloss] + blocks.flatMap(\.texts)).joined(separator: "\n").lowercased()
    }

    /// The notes split at their bold labels, one card each — see splitSections().
    let sections: [NoteSection]
    let searchText: String

    static func == (a: Entry, b: Entry) -> Bool { a.anchor == b.anchor && a.hash == b.hash }
    func hash(into h: inout Hasher) { h.combine(anchor) }
}

struct Example: Decodable, Hashable {
    let en: String
    let zh: String
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        en = try c.decode(String.self, forKey: .en)
        zh = try c.decodeIfPresent(String.self, forKey: .zh) ?? ""
    }
    enum CodingKeys: String, CodingKey { case en, zh }
}

/// Mirrors parse_blocks in build.py. Text keeps its inline markdown.
enum Block: Decodable, Hashable {
    case para(String)
    case bullets([String])
    case examples([Example])
    case quote([String])
    case table(head: [String], rows: [[String]])
    /// A block type from a newer build.py: skipped, so the rest still loads.
    case unknown

    enum CodingKeys: String, CodingKey { case t, text, items, lines, head, rows }

    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        switch try c.decode(String.self, forKey: .t) {
        case "p": self = .para(try c.decode(String.self, forKey: .text))
        case "ul": self = .bullets(try c.decode([String].self, forKey: .items))
        case "ol": self = .examples(try c.decode([Example].self, forKey: .items))
        case "quote": self = .quote(try c.decode([String].self, forKey: .lines))
        case "table": self = .table(head: try c.decode([String].self, forKey: .head),
                                    rows: try c.decodeIfPresent([[String]].self, forKey: .rows) ?? [])
        default: self = .unknown
        }
    }

    var texts: [String] {
        switch self {
        case .para(let t): return [t]
        case .bullets(let xs), .quote(let xs): return xs
        case .examples(let xs): return xs.flatMap { [$0.en, $0.zh] }
        case .table(let h, let rows): return h + rows.flatMap { $0 }
        case .unknown: return []
        }
    }
}

/// One card of the notes: [label] is its tab ("释义", "原句", "固定搭配").
struct NoteSection: Hashable { let label: String; let blocks: [Block] }

// A bold label opens a section: "**常见搭配:**", "**含义:** text", "**义项一:…**", "**race**(动词)".
private let sectionRE = try! NSRegularExpression(
    pattern: #"^\*\*([^*]+?)[:：]\s*\*\*|^\*\*([^*]+?)\*\*\s*(?:[(（][^)）]*[)）])?$"#)
// consecutive definition sections share one card
private let definition: Set<String> = ["English definition", "含义", "释义", "整体释义"]

private func sectionLabel(_ text: String) -> String? {
    let ns = text as NSString
    guard let m = sectionRE.firstMatch(in: text, range: NSRange(location: 0, length: ns.length)) else { return nil }
    let r = m.range(at: 1).location != NSNotFound ? m.range(at: 1) : m.range(at: 2)
    let label = ns.substring(with: r).replacingOccurrences(of: "⚠️", with: "").trimmingCharacters(in: .whitespaces)
    // a bracket cuts the label off ("race(动词)" -> "race"), except a numbered one, which stays:
    // 改好的文章（一） / 改好的文章（二） (用户 2026-11-09) — as section_label() in build.py
    let ls = label as NSString
    let cut = labelCutRE.firstMatch(in: label, range: NSRange(location: 0, length: ls.length))
    let head = cut.map { ls.substring(to: $0.range.location) } ?? label
    return head.trimmingCharacters(in: .whitespaces)
}
private let labelCutRE = try! NSRegularExpression(pattern: #"[:：—]|[(（](?![一二三四五六七八九十\d]+[)）])"#)

/// Mirrors sections() in build.py and splitSections() in Model.kt — keep them in step. Whatever comes
/// before the first label is 概要; English definition + 含义 become one 释义 card; long labels are cut.
func splitSections(_ blocks: [Block]) -> [NoteSection] {
    var out: [(String, [Block])] = []
    for b in blocks {
        var label: String? = nil
        if case .para(let t) = b { label = sectionLabel(t) }
        if let l = label, definition.contains(l) { label = out.last?.0 == "释义" ? nil : "释义" }
        if label != nil || out.isEmpty {
            let l = label ?? "概要"
            out.append((l.count > 11 ? String(l.prefix(10)) + "…" : l, []))
        }
        out[out.count - 1].1.append(b)
    }
    return out.map { NoteSection(label: $0.0, blocks: $0.1) }
}

/// The groups taobao's notes use, in the order the sidebar lists them.
let groupNames: [(key: String, label: String)] = [
    ("SENTENCE", "句子"), ("COLLOC", "固定搭配"), ("ESSAY", "文章"), ("SELFTEST", "自测题"),
    ("GRAMMAR", "语法"), ("TERM", "术语"), ("KNOWLEDGE", "Knowledge"),
    ("A1", "A1"), ("A2", "A2"), ("B1", "B1"), ("B2", "B2"), ("C1", "C1"), ("C2", "C2"),
]
func groupLabel(_ level: String) -> String { groupNames.first { $0.key == level }?.label ?? level }
