import SwiftUI

// 错题本 type tags, e.g. `[介词]` — keep in sync with MISTAKE_TYPES in build.py and Inline.kt.
let mistakeTags = ["冠词", "介词", "搭配", "动词", "结构", "词形", "标点", "逻辑", "直译", "语气"]

// Same inline syntax as build.py's inline(), plus the web's external links. The tag goes first so it
// is never swallowed into a later `[text](#anchor)`.
private let token = try! NSRegularExpression(pattern:
    #"\[("# + mistakeTags.joined(separator: "|") + #")\](?!\()|"# +
    #"\*\*(.+?)\*\*|~~(.+?)~~|`(.+?)`|\[(.+?)\]\(#([^)]+)\)|\[(.+?)\]\((https?://[^)]+)\)|"# +
    #"(?<![\w*])\*([^*\n]+)\*(?![\w*])|==(.+?)=="#)

/// Cross-references open as `vocab-xref:<anchor>`, handled by the entry view's OpenURLAction.
let xrefScheme = "vocab-xref"

private func groups(_ m: NSTextCheckingResult, _ ns: NSString) -> [String?] {
    (0..<m.numberOfRanges).map { m.range(at: $0).location == NSNotFound ? nil : ns.substring(with: m.range(at: $0)) }
}

/// Inline markdown as styled text; [bold] tints `**bold**` (nil keeps the text colour).
func inline(_ text: String, bold: Color? = nil) -> AttributedString {
    let ns = text as NSString
    var out = AttributedString()
    var last = 0
    for m in token.matches(in: text, range: NSRange(location: 0, length: ns.length)) {
        out += AttributedString(ns.substring(with: NSRange(location: last, length: m.range.location - last)))
        let g = groups(m, ns)
        var piece: AttributedString
        if let tag = g[1] {
            piece = AttributedString("\u{2009}\(tag)\u{2009}")
            piece.font = .system(size: 13, weight: .bold, design: .rounded)
            piece.backgroundColor = Theme.tag
        } else if let b = g[2] {
            piece = inline(b, bold: bold)
            for r in piece.runs { piece[r.range].inlinePresentationIntent = (r.inlinePresentationIntent ?? []).union(.stronglyEmphasized) }
            if let bold { piece.foregroundColor = bold }
        } else if let s = g[3] {
            piece = inline(s, bold: bold); piece.strikethroughStyle = .single
        } else if let c = g[4] {
            piece = AttributedString(c); piece.inlinePresentationIntent = .code
        } else if let t = g[5], let anchor = g[6] {
            piece = inline(t, bold: bold)
            piece.link = URL(string: "\(xrefScheme):\(anchor)")
            piece.underlineStyle = .single
        } else if let t = g[7], let url = g[8] {
            piece = inline(t, bold: bold) + AttributedString(" ↗")
            piece.link = URL(string: url)
        } else if let i = g[9] {
            piece = inline(i, bold: bold)
            for r in piece.runs { piece[r.range].inlinePresentationIntent = (r.inlinePresentationIntent ?? []).union(.emphasized) }
        } else {
            piece = inline(g[10] ?? "", bold: bold); piece.backgroundColor = Theme.mistakeWash
        }
        out += piece
        last = m.range.location + m.range.length
    }
    out += AttributedString(ns.substring(from: last))
    return out
}

/// Plain text with the markdown markers dropped — what quiz answers and speech use.
func plain(_ text: String) -> String {
    let ns = text as NSString
    var out = ""
    var last = 0
    for m in token.matches(in: text, range: NSRange(location: 0, length: ns.length)) {
        out += ns.substring(with: NSRange(location: last, length: m.range.location - last))
        let g = groups(m, ns)
        if let tag = g[1] { out += "[\(tag)]" }
        else { out += [g[2], g[3], g[4], g[5], g[7], g[9], g[10]].compactMap { $0 }.first.map(plain) ?? "" }
        last = m.range.location + m.range.length
    }
    return out + ns.substring(from: last)
}
