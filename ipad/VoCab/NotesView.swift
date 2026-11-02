import SwiftUI

/// 中英切换 on an essay's notes (用户 2026-11-01): both, English only, Chinese only. Kept on this iPad.
enum EssayLang: String, CaseIterable {
    case both, en, zh
    var short: String { ["中英", "英", "中"][Self.allCases.firstIndex(of: self)!] }
    var next: EssayLang { Self.allCases[(Self.allCases.firstIndex(of: self)! + 1) % 3] }
}

/// Pages after the notes themselves: 记忆 (the forgetting curve) and 我的笔记.
enum NotesExtra: Hashable {
    case memory, note
    var label: String { self == .memory ? "记忆" : "我的笔记" }
}

/// The notes in one card: a tab per section (卡片), or everything in one long page (全部), then [extras].
/// [unseen] puts a dot on the pages still to read; [page] lets the caller follow and turn the pages.
struct NotesCard<Header: View, Footer: View>: View {
    let entry: Entry
    /// The sentence 朗读全文 is on (plain English), highlighted.
    var reading: String?
    var extras: [NotesExtra] = []
    var unseen: Set<Int> = []
    var page: Binding<Int>?
    @ViewBuilder var header: Header
    @ViewBuilder var footer: Footer

    @AppStorage("notesAll") private var all = false
    @AppStorage("essayLang") private var langRaw = EssayLang.both.rawValue
    @State private var ownPage = 0

    private var lang: EssayLang { entry.isEssay ? EssayLang(rawValue: langRaw) ?? .both : .both }
    private var at: Binding<Int> { page ?? $ownPage }
    private var labels: [String] { entry.sections.map(\.label) + extras.map(\.label) }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                if !all {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(Array(labels.enumerated()), id: \.offset) { k, label in
                                let on = at.wrappedValue == k
                                Button { withAnimation(.snappy) { at.wrappedValue = k } } label: {
                                    HStack(spacing: 6) {
                                        Text(label)
                                        // a page still to read (用户 2026-11-01)
                                        if unseen.contains(k) { Circle().fill(Theme.blue).frame(width: 7, height: 7) }
                                    }
                                    .font(Theme.font(15, .bold))
                                    .padding(.horizontal, 12).padding(.vertical, 7)
                                    .foregroundStyle(on ? Theme.selInk : Theme.ink2)
                                    .background(Capsule().fill(on ? Theme.selBg : .clear))
                                    .overlay(Capsule().stroke(on ? Theme.selEdge : .clear, lineWidth: 2))
                                }.buttonStyle(.plain)
                            }
                        }
                    }
                } else {
                    Text("笔记").font(Theme.font(15, .bold)).foregroundStyle(Theme.ink2)
                    Spacer()
                }
                if entry.isEssay {
                    Button { langRaw = lang.next.rawValue } label: {
                        Text(lang.short).font(Theme.font(14, .heavy)).padding(.horizontal, 10).padding(.vertical, 5)
                            .overlay(Capsule().stroke(Theme.rule, lineWidth: 2))
                    }.buttonStyle(.plain).foregroundStyle(Theme.ink2)
                    .accessibilityLabel("切换中英:现在是\(lang.short)")
                }
                header
                Button { all.toggle() } label: {
                    Image(systemName: all ? "rectangle.on.rectangle" : "text.justify.leading").font(.system(size: 17, weight: .semibold))
                        .frame(width: 34, height: 34)
                }.buttonStyle(.plain).foregroundStyle(Theme.ink2).accessibilityLabel(all ? "分卡显示" : "全部展开")
            }
            .padding(.horizontal, 14).padding(.vertical, 10)

            Divider().overlay(Theme.rule)

            if all {
                ScrollView {
                    VStack(alignment: .leading, spacing: 22) {
                        ForEach(Array(entry.sections.enumerated()), id: \.offset) { _, s in
                            VStack(alignment: .leading, spacing: 10) {
                                Text(s.label).font(Theme.font(13, .heavy)).foregroundStyle(Theme.ink3).textCase(.uppercase)
                                BlocksView(blocks: s.blocks, lang: lang, reading: reading)
                            }
                        }
                        ForEach(extras, id: \.self) { x in
                            VStack(alignment: .leading, spacing: 10) {
                                Text(x.label).font(Theme.font(13, .heavy)).foregroundStyle(Theme.ink3)
                                extra(x)
                            }
                        }
                    }.padding(20).frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                TabView(selection: at) {
                    ForEach(Array(entry.sections.enumerated()), id: \.offset) { k, s in
                        ScrollView {
                            BlocksView(blocks: s.blocks, lang: lang, reading: reading)
                                .padding(20).frame(maxWidth: .infinity, alignment: .leading)
                        }.tag(k)
                    }
                    ForEach(Array(extras.enumerated()), id: \.element) { j, x in
                        ScrollView {
                            extra(x).padding(20).frame(maxWidth: .infinity, alignment: .leading)
                        }.tag(entry.sections.count + j)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            }
            footer
        }
        .environment(\.exampleEntry, entry)
        .onChange(of: entry.anchor) { at.wrappedValue = 0 }
        .onChange(of: reading) {
            // 朗读全文 turns to the page with the sentence being read
            guard let r = reading, !all else { return }
            if let k = entry.sections.firstIndex(where: { $0.blocks.contains { b in
                if case .examples(let xs) = b { return xs.contains { plain($0.en) == r } } else { return false } } }), k != at.wrappedValue {
                withAnimation { at.wrappedValue = k }
            }
        }
    }

    @ViewBuilder private func extra(_ x: NotesExtra) -> some View {
        switch x {
        case .memory: MemoryCard(entry: entry)
        case .note: MyNote(entry: entry)
        }
    }
}

/// The entry whose examples a BlocksView shows: with it, they get ⭐, 不再考这句 and the long-press menu.
private struct ExampleEntryKey: EnvironmentKey { static let defaultValue: Entry? = nil }
extension EnvironmentValues {
    var exampleEntry: Entry? {
        get { self[ExampleEntryKey.self] }
        set { self[ExampleEntryKey.self] = newValue }
    }
}

/// The blocks of one section, as the page lays them out.
struct BlocksView: View {
    let blocks: [Block]
    var lang: EssayLang = .both
    var reading: String?
    /// The sentence a question asks, highlighted in 笔记里的这一句.
    var lit: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { _, b in block(b) }
        }
        .font(Theme.font(19, .medium))
        .foregroundStyle(Theme.ink)
        .lineSpacing(4)
        .tint(Theme.selInk)
    }

    @ViewBuilder private func block(_ b: Block) -> some View {
        switch b {
        case .para(let t):
            Text(inline(t)).frame(maxWidth: .infinity, alignment: .leading)
        case .bullets(let items):
            VStack(alignment: .leading, spacing: 8) {
                ForEach(Array(items.enumerated()), id: \.offset) { _, t in
                    HStack(alignment: .firstTextBaseline, spacing: 10) {
                        Text("•").foregroundStyle(Theme.ink3)
                        Text(inline(t)).frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        case .examples(let xs):
            VStack(alignment: .leading, spacing: 10) {
                ForEach(Array(xs.enumerated()), id: \.offset) { k, x in example(k, x) }
            }
        case .quote(let lines):
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 2).fill(Theme.rule).frame(width: 4)
                Text(inline(lines.joined(separator: "\n"))).foregroundStyle(Theme.ink2)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }.fixedSize(horizontal: false, vertical: true)
        case .table(let head, let rows):
            ScrollView(.horizontal) {
                Grid(alignment: .topLeading, horizontalSpacing: 16, verticalSpacing: 10) {
                    GridRow { ForEach(Array(head.enumerated()), id: \.offset) { Text(inline($1)).font(Theme.font(16, .heavy)) } }
                    Divider()
                    ForEach(Array(rows.enumerated()), id: \.offset) { _, r in
                        GridRow { ForEach(Array(r.enumerated()), id: \.offset) { Text(inline($1)).font(Theme.font(16, .medium)).frame(maxWidth: 320, alignment: .leading) } }
                    }
                }
            }
        case .unknown:
            EmptyView()
        }
    }

    private func example(_ k: Int, _ x: Example) -> some View {
        ExampleRow(k: k, x: x, lang: lang, lit: reading == plain(x.en) || lit == plain(x.en))
    }
}

/// One numbered example. With an entry around (exampleEntry), an example it asks has its number in a
/// solid dot — hollow and faded once left out (不再考这句) — a ⭐ when starred, and the long-press menu:
/// 复制 · 收藏 / 取消收藏 · 不再考这句 / 恢复考这句. Same as exMenu() on the page and ExampleMenu on the phone.
private struct ExampleRow: View {
    let k: Int
    let x: Example
    let lang: EssayLang
    let lit: Bool
    @Environment(\.exampleEntry) private var entry
    @Environment(AppModel.self) private var model
    @State private var refused = false

    var body: some View {
        let en = plain(x.en)
        let showEn = lang != .zh || x.zh.isEmpty
        let showZh = lang != .en && !x.zh.isEmpty
        let asked = entry.map { $0.level != "SELFTEST" && !$0.isEssay && x.quizable } ?? false
        let out = asked && entry.map { model.isOut($0, en) } == true
        let fav = entry.map { model.isFav($0, en) } ?? false
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            number(asked: asked, out: out)
            VStack(alignment: .leading, spacing: 4) {
                if showEn {
                    HStack(alignment: .firstTextBaseline) {
                        (Text(inline(x.en)) + Text(fav ? " ⭐" : "")).frame(maxWidth: .infinity, alignment: .leading)
                        SpeakButton(text: en)
                    }
                }
                // only Chinese: it takes the body's size and colour
                if showZh { Text(inline(x.zh)).font(Theme.font(showEn ? 16 : 19, .medium)).foregroundStyle(showEn ? Theme.ink2 : Theme.ink) }
                if refused { Text("至少要留一句例句来考").font(Theme.font(13, .bold)).foregroundStyle(Theme.badInk) }
            }
            .opacity(out ? 0.45 : 1)
        }
        .padding(.vertical, 6).padding(.horizontal, 8)
        .background(RoundedRectangle(cornerRadius: 10).fill(lit ? Theme.selBg : .clear))
        .contentShape(RoundedRectangle(cornerRadius: 10))
        .animation(.easeOut(duration: 0.2), value: lit)
        .contextMenu {
            if let entry {
                Button { UIPasteboard.general.string = en } label: { Label("复制", systemImage: "doc.on.doc") }
                Button { model.toggleFav(entry, en) } label: {
                    Label(fav ? "取消收藏" : "收藏", systemImage: fav ? "star.slash" : "star")
                }
                if asked {
                    Button {
                        if !model.toggleOut(entry, en) { refused = true; DispatchQueue.main.asyncAfter(deadline: .now() + 2) { refused = false } }
                    } label: {
                        Label(out ? "恢复考这句" : "不再考这句", systemImage: out ? "checkmark.circle" : "nosign")
                    }
                }
            }
        }
    }

    @ViewBuilder private func number(asked: Bool, out: Bool) -> some View {
        if asked {
            Text("\(k + 1)").font(Theme.font(12, .heavy)).foregroundStyle(out ? Theme.ink3 : .white)
                .frame(width: 22, height: 22)
                .background(Circle().fill(out ? Color.clear : Theme.ink3))
                .overlay(Circle().stroke(Theme.ink3, lineWidth: out ? 1.5 : 0))
        } else {
            Text("\(k + 1)").font(Theme.font(14, .heavy)).foregroundStyle(Theme.ink3).frame(minWidth: 22)
        }
    }
}

/// 🔊 — reads [text] aloud.
struct SpeakButton: View {
    let text: String
    var size: CGFloat = 16
    var body: some View {
        Button { Speaker.shared.speak(text) } label: {
            Image(systemName: "speaker.wave.2.fill").font(.system(size: size, weight: .semibold)).foregroundStyle(Theme.ink3)
                .frame(width: size + 18, height: size + 14)
        }.buttonStyle(.plain).accessibilityLabel("朗读")
    }
}
