import SwiftUI

/// 中英切换 on an essay's notes (用户 2026-11-01): both, English only, Chinese only. Kept on this iPad.
enum EssayLang: String, CaseIterable {
    case both, en, zh
    var short: String { ["中英", "英", "中"][Self.allCases.firstIndex(of: self)!] }
    var next: EssayLang { Self.allCases[(Self.allCases.firstIndex(of: self)! + 1) % 3] }
}

/// The notes in one card: a tab per section (卡片), or everything in one long page (全部).
struct NotesCard<Header: View, Footer: View>: View {
    let entry: Entry
    /// The sentence 朗读全文 is on (plain English), highlighted.
    var reading: String?
    @ViewBuilder var header: Header
    @ViewBuilder var footer: Footer

    @AppStorage("notesAll") private var all = false
    @AppStorage("essayLang") private var langRaw = EssayLang.both.rawValue
    @State private var page = 0

    private var lang: EssayLang { entry.isEssay ? EssayLang(rawValue: langRaw) ?? .both : .both }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                if !all {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(Array(entry.sections.enumerated()), id: \.offset) { k, s in
                                Button { withAnimation(.snappy) { page = k } } label: {
                                    Text(s.label).font(Theme.font(15, .bold))
                                        .padding(.horizontal, 12).padding(.vertical, 7)
                                        .foregroundStyle(page == k ? Theme.selInk : Theme.ink2)
                                        .background(Capsule().fill(page == k ? Theme.selBg : .clear))
                                        .overlay(Capsule().stroke(page == k ? Theme.selEdge : .clear, lineWidth: 2))
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
                    }.padding(20).frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                TabView(selection: $page) {
                    ForEach(Array(entry.sections.enumerated()), id: \.offset) { k, s in
                        ScrollView {
                            BlocksView(blocks: s.blocks, lang: lang, reading: reading)
                                .padding(20).frame(maxWidth: .infinity, alignment: .leading)
                        }.tag(k)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            }
            footer
        }
        .onChange(of: entry.anchor) { page = 0 }
        .onChange(of: reading) {
            // 朗读全文 turns to the page with the sentence being read
            guard let r = reading, !all else { return }
            if let k = entry.sections.firstIndex(where: { $0.blocks.contains { b in
                if case .examples(let xs) = b { return xs.contains { plain($0.en) == r } } else { return false } } }), k != page {
                withAnimation { page = k }
            }
        }
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
        let en = plain(x.en)
        let on = reading == en || lit == en
        let showEn = lang != .zh || x.zh.isEmpty
        let showZh = lang != .en && !x.zh.isEmpty
        return HStack(alignment: .firstTextBaseline, spacing: 10) {
            Text("\(k + 1)").font(Theme.font(14, .heavy)).foregroundStyle(Theme.ink3).frame(minWidth: 18)
            VStack(alignment: .leading, spacing: 4) {
                if showEn {
                    HStack(alignment: .firstTextBaseline) {
                        Text(inline(x.en)).frame(maxWidth: .infinity, alignment: .leading)
                        SpeakButton(text: en)
                    }
                }
                // only Chinese: it takes the body's size and colour
                if showZh { Text(inline(x.zh)).font(Theme.font(showEn ? 16 : 19, .medium)).foregroundStyle(showEn ? Theme.ink2 : Theme.ink) }
            }
        }
        .padding(.vertical, 6).padding(.horizontal, 8)
        .background(RoundedRectangle(cornerRadius: 10).fill(on ? Theme.selBg : .clear))
        .animation(.easeOut(duration: 0.2), value: on)
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
