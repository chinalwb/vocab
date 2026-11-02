import SwiftUI

/*
 * One question's answer sheet, shared by the entry's 自测 and the 复习 / 考核 runs (AnswerSheet.kt on the
 * phone, qsheetHtml() + wireAnswer() on the page): ① built from chips, then ② typed — both right for the
 * sentence to count. A sentence answered right before skips ①; [fill] makes ② type only the **bold** key
 * part. An essay asks ① only. [onAnswer] gets the final result once (the caller records it); a new
 * sentence or a fresh try is a new sheet (the caller changes its .id).
 */
/// How a sheet's question went; [final] is false after ① (checked, not logged).
struct SheetOutcome { let diff: WordDiff; let draft: String; var ok: Bool; let steps: String?; let final: Bool }

struct AnswerSheet<Lead: View, Trail: View, After: View>: View {
    let entry: Entry
    let q: Question
    /// what the attempt log knows this question by: plain English, "" for a 自测题
    let key: String
    let fill: Bool
    /// the entry's other sentences, for the stray chips
    let others: [String]
    let onAnswer: (_ text: String, _ ok: Bool) -> Void
    let lead: Lead
    let trail: Trail
    /// under the verdict: the caller's buttons; [markRight] turns the verdict right (算我对)
    let after: (_ r: SheetOutcome, _ markRight: @escaping () -> Void) -> After

    init(entry: Entry, q: Question, key: String, fill: Bool, others: [String],
         onAnswer: @escaping (String, Bool) -> Void,
         @ViewBuilder lead: () -> Lead, @ViewBuilder trail: () -> Trail,
         @ViewBuilder after: @escaping (SheetOutcome, @escaping () -> Void) -> After) {
        self.entry = entry; self.q = q; self.key = key; self.fill = fill; self.others = others
        self.onAnswer = onAnswer; self.lead = lead(); self.trail = trail(); self.after = after
    }

    @Environment(AppModel.self) private var model
    @State private var step = 1
    @State private var skipped = false
    @State private var chips = true
    @State private var picked: [String] = []
    @State private var typed = ""
    @State private var fills: [String] = []
    @State private var bankOk = false
    @State private var revealed = false
    @State private var result: SheetOutcome?
    @State private var started = false
    @State private var righted = false
    @FocusState private var focus: Int?


    private var stage: Stage? { model.stage(entry) }
    private var blanks: [Blank]? { fill ? blanksOf(q.raw) : nil }
    private var bank: WordBank {
        let first = q.en.split(separator: "\n").first.map(String.init) ?? q.en
        return WordBank(answer: first, strays: strayWords(first, others: others.filter { $0 != q.en }, seed: q.k + 7), seed: q.k + 1)
    }
    private var draft: String {
        if chips { return picked.joined(separator: " ") }
        if let b = blanks { return composeBlanks(b, fills) }
        return typed.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    topLine
                    promptCard
                    if !revealed {
                        if chips { bankPanel(bank) }
                        Button(action: reveal) { Text("显示答案").frame(maxWidth: .infinity) }
                            .buttonStyle(LedgeButtonStyle(face: Theme.blue, edge: Theme.blueEdge))
                    } else if let result {
                        verdict(result).id("verdict")
                    }
                }
                .padding(20)
            }
            .onChange(of: revealed) { if revealed { withAnimation { proxy.scrollTo("verdict", anchor: .top) } } }
        }
        .onAppear {
            guard !started else { return }
            started = true
            // answered right before → straight to ② 打字
            let skip = !entry.isEssay && model.rightBefore(entry, key)
            begin(step: skip ? 2 : 1, skip: skip)
        }
    }

    // MARK: pieces

    private var topLine: some View {
        HStack(spacing: 10) {
            lead
            if !entry.isEssay { Text(step == 1 ? "① 选词" : "② 打字").font(Theme.font(15, .heavy)).foregroundStyle(Theme.selInk) }
            Spacer()
            trail
            if step == 2 && !revealed && !entry.isEssay {
                HStack(spacing: 2) {
                    segment("选词", on: chips) { chips = true; picked = [] }
                    segment("打字", on: !chips) { chips = false }
                }.padding(2).overlay(Capsule().stroke(Theme.rule, lineWidth: 2))
            }
        }
    }

    private func segment(_ t: String, on: Bool, _ act: @escaping () -> Void) -> some View {
        Button(action: act) {
            Text(t).font(Theme.font(14, .bold)).padding(.horizontal, 12).padding(.vertical, 5)
                .foregroundStyle(on ? Theme.ground : Theme.ink2).background(Capsule().fill(on ? Theme.ink : .clear))
        }.buttonStyle(.plain)
    }

    private var promptCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(stats).font(Theme.font(13, .semibold)).foregroundStyle(Theme.ink3)
            Text(q.zh).font(Theme.font(picked.isEmpty && typed.isEmpty ? 26 : 22, .bold)).foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)
            answerBox
        }
        .padding(20)
        .ledgeCard(Theme.wash(stage))
    }

    /// This question's history: the stats line counts only this sentence.
    private var stats: String {
        let mine = model.attempts(entry).filter { $0.ex == key }
        let tries = mine.filter { !$0.text.isEmpty }
        let one = !key.isEmpty
        guard let last = mine.last else { return one ? "这一句:还没自测过" : "还没自测过" }
        let mark = last.text.isEmpty ? "偷看" : last.ok ? "✅" : "❌"
        return (one ? "这一句:" : "") + "已尝试 \(tries.count) 次 · 答对 \(tries.filter(\.ok).count) 次 · 上次 \(mark)"
    }

    @ViewBuilder private var answerBox: some View {
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        Group {
            if chips {
                if picked.isEmpty {
                    Text(revealed ? "(没有选词)" : "点下面的词,拼出这句英文").foregroundStyle(Theme.ink3)
                } else {
                    FlowLayout(spacing: 6) {
                        ForEach(Array(picked.enumerated()), id: \.offset) { i, w in
                            let lastOne = i == picked.count - 1 && !revealed
                            Text(w).font(Theme.font(20, .bold)).padding(.horizontal, 10).padding(.vertical, 6)
                                .background(RoundedRectangle(cornerRadius: 10).fill(lastOne ? Theme.selBg : Theme.soft))
                                .onTapGesture { if lastOne { picked.removeLast() } }
                        }
                    }
                }
            } else if let b = blanks {
                blanksView(b)
            } else {
                TextField("用英文写这一句", text: $typed, axis: .vertical)
                    .font(Theme.font(21, .semibold)).lineLimit(1...8)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .focused($focus, equals: 0).disabled(revealed)
                    .onSubmit(reveal)
            }
        }
        .padding(14).frame(maxWidth: .infinity, minHeight: 64, alignment: .topLeading)
        .background(shape.fill(Theme.ground))
        .overlay(shape.stroke(!chips && !revealed ? Theme.selEdge : Theme.rule, lineWidth: 2))
    }

    /// 打字只填关键部分: the filled-in words flow round one field per **bold** part.
    private func blanksView(_ parts: [Blank]) -> some View {
        var keyNo = -1
        let pieces: [(key: Int?, text: String)] = parts.flatMap { p -> [(key: Int?, text: String)] in
            if p.key { keyNo += 1; return [(keyNo, p.text)] }
            return words(p.text).map { (nil, $0) }
        }
        return FlowLayout(spacing: 6, lineSpacing: 10) {
            ForEach(Array(pieces.enumerated()), id: \.offset) { _, piece in
                if let i = piece.key {
                    let width = CGFloat(max(piece.text.count, i < fills.count ? fills[i].count : 0) + 2) * 12
                    TextField("", text: Binding(get: { i < fills.count ? fills[i] : "" }, set: { if i < fills.count { fills[i] = $0 } }))
                        .font(Theme.font(21, .bold)).foregroundStyle(Theme.selInk)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                        .focused($focus, equals: i).disabled(revealed)
                        .submitLabel(i + 1 < fills.count ? .next : .done)
                        .onSubmit { if i + 1 < fills.count { focus = i + 1 } else { reveal() } }
                        .frame(width: min(width, 520))
                        .padding(.vertical, 4).padding(.horizontal, 6)
                        .background(RoundedRectangle(cornerRadius: 8).fill(Theme.selBg))
                } else {
                    Text(piece.text).font(Theme.font(21, .semibold)).foregroundStyle(Theme.ink2)
                }
            }
        }
    }

    private func bankPanel(_ bank: WordBank) -> some View {
        let opts = bank.layout(after: picked.count)
        return HStack(alignment: .top, spacing: 12) {
            if opts.isEmpty {
                Text("拼完了,点「显示答案」对一下").font(Theme.font(17, .bold)).foregroundStyle(Theme.ink3)
                    .frame(maxWidth: .infinity, minHeight: 120)
            } else {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible())], spacing: 12) {
                    ForEach(Array(opts.enumerated()), id: \.offset) { _, w in
                        Button {
                            picked.append(w)
                            Speaker.shared.speak(w.trimmingCharacters(in: .punctuationCharacters)) // 选一个词就读出来 (用户 2026-10-31)
                        } label: { Text(w).frame(maxWidth: .infinity) }
                        .buttonStyle(LedgeButtonStyle(outline: true))
                    }
                }
                .frame(maxWidth: .infinity)
            }
            Button { if !picked.isEmpty { picked.removeLast() } } label: {
                Image(systemName: "delete.left").font(.system(size: 22, weight: .bold)).frame(width: 30)
            }
            .buttonStyle(LedgeButtonStyle(outline: true)).disabled(picked.isEmpty).opacity(picked.isEmpty ? 0.4 : 1)
            .accessibilityLabel("撤回")
        }
    }

    // MARK: verdict

    @ViewBuilder private func verdict(_ r: SheetOutcome) -> some View {
        let d = r.diff
        let bar: String = r.draft.isEmpty ? "没写就看了答案" : d.same ? "✅ 对了!" :
            (["差一点"] + (d.extra > 0 ? ["多了 \(d.extra) 个词"] : []) + (d.missing > 0 ? ["漏了 \(d.missing) 个词"] : [])).joined(separator: " · ")
        VStack(alignment: .leading, spacing: 12) {
            Text(bar).font(Theme.font(18, .heavy)).foregroundStyle(d.same ? Theme.okInk : Theme.badInk)
                .padding(.horizontal, 16).padding(.vertical, 12).frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 14).fill(d.same ? Theme.okBg : Theme.badBg))
            if !r.draft.isEmpty { line("我写的", mineText(d)) }
            line("答案", answerText(d), say: d.answer.joined(separator: " "))
            if let s = r.steps {
                Text(righted ? "算我对 — 这一句记为答对" : s).font(Theme.font(15, .semibold)).padding(12).frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 12).fill(r.ok ? Theme.okBg : Theme.missBg))
            }
            if r.final {
                after(r) { result?.ok = true; righted = true }
            } else {
                Button("下一步:打字 ›") { begin(step: 2) }.buttonStyle(LedgeButtonStyle())
            }
            // 笔记里的这一句
            if !key.isEmpty, let s = entry.sections.first(where: { $0.blocks.contains { b in
                if case .examples(let xs) = b { return xs.contains { plain($0.en) == q.en } } else { return false } } }) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("笔记里的这一句 · \(s.label)").font(Theme.font(13, .heavy)).foregroundStyle(Theme.ink3)
                    BlocksView(blocks: s.blocks, lit: q.en)
                }.padding(18).ledgeCard()
            }
        }
    }

    private func line(_ label: String, _ text: AttributedString, say: String? = nil) -> some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 4) {
                Text(label).font(Theme.font(12, .heavy)).foregroundStyle(Theme.ink3)
                Text(text).font(Theme.font(20, .semibold))
            }
            Spacer()
            if let say { SpeakButton(text: say) }
        }
        .padding(14).background(RoundedRectangle(cornerRadius: 12).fill(Theme.soft))
    }

    private func mineText(_ d: WordDiff) -> AttributedString {
        d.mine.enumerated().reduce(into: AttributedString()) { out, w in
            var a = AttributedString((w.offset > 0 ? " " : "") + w.element)
            if !d.keepMine.contains(w.offset) { a.strikethroughStyle = .single; a.foregroundColor = Theme.badInk }
            out += a
        }
    }
    private func answerText(_ d: WordDiff) -> AttributedString {
        d.answer.enumerated().reduce(into: AttributedString()) { out, w in
            if w.offset > 0 { out += AttributedString(" ") }
            var a = AttributedString(w.element)
            if !d.keepAnswer.contains(w.offset) { a.backgroundColor = Theme.missBg; a.foregroundColor = Theme.missInk }
            out += a
        }
    }

    // MARK: flow

    /// [s] 1 = ① 选词, 2 = ② 打字; [skip] when ① was skipped because the sentence was right before.
    private func begin(step s: Int, skip: Bool = false) {
        skipped = skip
        if s == 1 { bankOk = false }
        step = s
        chips = s == 1
        picked = []; typed = ""
        fills = Array(repeating: "", count: blanks?.filter(\.key).count ?? 0)
        revealed = false; result = nil
        if s == 2 { DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { focus = 0 } }
    }

    private func reveal() {
        guard !revealed else { return }
        focus = nil
        let text = draft
        let d = bestDiff(text, q.en)
        let right = d.same && !text.isEmpty
        if step == 1 && !entry.isEssay {
            // ① is checked, not logged
            bankOk = right
            result = SheetOutcome(diff: d, draft: text, ok: right, steps: nil, final: false)
        } else {
            let helped = step == 2 && chips
            let typedOk = right && !helped
            let ok = entry.isEssay ? right : typedOk && (skipped || bankOk)
            let typedMark = typedOk ? "✓" : helped ? "✗(用选词交的)" : "✗"
            let steps: String? = entry.isEssay ? nil : skipped
                ? "打字 \(typedMark) — 这一句以前答对过,跳过了选词;" + (typedOk ? "这一句算答对" : "这次记为没对")
                : "选词 \(bankOk ? "✓" : "✗") · 打字 \(typedMark) — " + (bankOk && typedOk ? "两步都对,这一句算答对" : "两步都对才算答对,这次记为没对")
            result = SheetOutcome(diff: d, draft: text, ok: ok, steps: steps, final: true)
            onAnswer(text, ok)
        }
        withAnimation(.snappy) { revealed = true }
    }
}

extension AnswerSheet where Trail == EmptyView {
    init(entry: Entry, q: Question, key: String, fill: Bool, others: [String],
         onAnswer: @escaping (String, Bool) -> Void,
         @ViewBuilder lead: () -> Lead,
         @ViewBuilder after: @escaping (SheetOutcome, @escaping () -> Void) -> After) {
        self.init(entry: entry, q: q, key: key, fill: fill, others: others, onAnswer: onAnswer, lead: lead, trail: { EmptyView() }, after: after)
    }
}
