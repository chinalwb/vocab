import SwiftUI

/*
 * The entry's 自测, the back of its card. Same rules as the web's show() / the Android AnswerSheet:
 * each sentence is ① built from chips, then ② typed — both right for the sentence to count. A sentence
 * answered right before skips ①; in 学习中 / 复习 ② types only the **bold** key part (fillBlanks), in
 * 考核 the whole sentence. An essay asks 选词 only, in note order. Once every sentence has been right
 * since the entry entered 学习中, it moves to 复习 by itself.
 */
struct QuizView: View {
    let entry: Entry
    /// Back to the notes; nil in 考核 until the question is answered.
    var onNotes: (() -> Void)?
    /// 下一条 › — the next entry of the list it was opened from.
    var onNextEntry: (() -> Void)?

    @Environment(AppModel.self) private var model

    @State private var order: [Int]?
    @State private var pos = 0
    @State private var done: Set<Int> = []
    @State private var step = 1
    @State private var skipped = false
    @State private var chips = true
    @State private var picked: [String] = []
    @State private var typed = ""
    @State private var fills: [String] = []
    @State private var bankOk = false
    @State private var revealed = false
    @State private var result: Outcome?
    @State private var note: String?
    @FocusState private var focus: Int?

    struct Outcome { let diff: WordDiff; let draft: String; let ok: Bool; let steps: String?; let final: Bool }

    private var stage: Stage? { model.stage(entry) }
    private var xs: [Example] { entry.quizExamples }
    private var n: Int { xs.count }
    private var selfTest: Bool { entry.level == "SELFTEST" }

    private var k: Int { n == 0 ? 0 : order.map { $0[pos % n] } ?? pos % n }
    private var q: Question? {
        if n > 0 { let x = xs[k]; return Question(en: plain(x.en), zh: plain(x.zh), raw: x.en, k: k, of: n) }
        if selfTest { let a = entry.selfTestAnswer; return Question(en: a, zh: entry.title, raw: a, k: 0, of: 1) }
        return nil
    }
    private var key: String { selfTest ? "" : q?.en ?? "" }
    private var blanks: [Blank]? {
        guard let q, stage != .test, !entry.isEssay, !selfTest else { return nil }
        return blanksOf(q.raw)
    }
    private var bank: WordBank? {
        guard let q else { return nil }
        let first = q.en.split(separator: "\n").first.map(String.init) ?? q.en
        return WordBank(answer: first, strays: strayWords(first, others: xs.map { plain($0.en) }.filter { $0 != q.en }, seed: q.k + 7),
                        seed: q.k + 1)
    }
    private var draft: String {
        if chips { return picked.joined(separator: " ") }
        if let b = blanks { return composeBlanks(b, fills) }
        return typed.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        if let q {
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        topLine(q)
                        promptCard(q)
                        if !revealed {
                            if chips, let bank { bankPanel(bank) }
                            Button(action: reveal) { Text("显示答案").frame(maxWidth: .infinity) }
                                .buttonStyle(LedgeButtonStyle(face: Theme.blue, edge: Theme.blueEdge))
                        } else if let result {
                            verdict(q, result).id("verdict")
                        }
                    }
                    .padding(20)
                }
                .onChange(of: revealed) { if revealed { withAnimation { proxy.scrollTo("verdict", anchor: .top) } } }
            }
            .onAppear(perform: start)
            .onChange(of: entry.anchor) { start() }
        } else {
            SelfGraded(entry: entry)
        }
    }

    // MARK: pieces

    private func topLine(_ q: Question) -> some View {
        HStack(spacing: 10) {
            if let onNotes {
                Button(action: onNotes) { Label("看笔记", systemImage: "chevron.left").font(Theme.font(15, .bold)) }
                    .buttonStyle(.plain).foregroundStyle(Theme.ink2)
            }
            Text(n > 0 ? "例句 \(q.k + 1) / \(q.of)" : "自测题").font(Theme.font(15, .heavy)).foregroundStyle(Theme.ink2)
            if !entry.isEssay { Text(step == 1 ? "① 选词" : "② 打字").font(Theme.font(15, .heavy)).foregroundStyle(Theme.selInk) }
            Spacer()
            if n > 1 && !entry.isEssay { pill("打乱", system: "shuffle", on: order != nil) { shuffle() } }
            if step == 2 && !revealed && !entry.isEssay {
                HStack(spacing: 2) {
                    segment("选词", on: chips) { chips = true; picked = [] }
                    segment("打字", on: !chips) { chips = false }
                }.padding(2).overlay(Capsule().stroke(Theme.rule, lineWidth: 2))
            }
        }
    }

    private func pill(_ t: String, system: String, on: Bool, _ act: @escaping () -> Void) -> some View {
        Button(action: act) {
            Label(t, systemImage: system).font(Theme.font(14, .bold))
                .padding(.horizontal, 12).padding(.vertical, 6)
                .foregroundStyle(on ? Theme.ground : Theme.ink2)
                .background(Capsule().fill(on ? Theme.ink : Theme.soft))
        }.buttonStyle(.plain)
    }

    private func segment(_ t: String, on: Bool, _ act: @escaping () -> Void) -> some View {
        Button(action: act) {
            Text(t).font(Theme.font(14, .bold)).padding(.horizontal, 12).padding(.vertical, 5)
                .foregroundStyle(on ? Theme.ground : Theme.ink2).background(Capsule().fill(on ? Theme.ink : .clear))
        }.buttonStyle(.plain)
    }

    private func promptCard(_ q: Question) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(stats).font(Theme.font(13, .semibold)).foregroundStyle(Theme.ink3)
            Text(q.zh).font(Theme.font(picked.isEmpty && typed.isEmpty ? 26 : 22, .bold)).foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)
            answerBox
        }
        .padding(20)
        .ledgeCard(Theme.wash(stage))
    }

    private var stats: String {
        let mine = model.attempts(entry).filter { $0.ex == key }
        let tries = mine.filter { !$0.text.isEmpty }
        guard let last = mine.last else { return n > 0 ? "这一句:还没自测过" : "还没自测过" }
        let mark = last.text.isEmpty ? "偷看" : last.ok ? "✅" : "❌"
        return (n > 0 ? "这一句:" : "") + "已尝试 \(tries.count) 次 · 答对 \(tries.filter(\.ok).count) 次 · 上次 \(mark)"
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
                            Speaker.shared.speak(replaceEdge(w)) // 选一个词就读出来 (用户 2026-10-31)
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

    private func replaceEdge(_ w: String) -> String { w.trimmingCharacters(in: .punctuationCharacters) }

    // MARK: verdict

    @ViewBuilder private func verdict(_ q: Question, _ r: Outcome) -> some View {
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
                Text(s).font(Theme.font(15, .semibold)).padding(12).frame(maxWidth: .infinity, alignment: .leading)
                    .background(RoundedRectangle(cornerRadius: 12).fill(r.ok ? Theme.okBg : Theme.missBg))
            }
            if let note { Text(note).font(Theme.font(15, .bold)).foregroundStyle(Theme.okInk) }
            buttons(r)
            // 笔记里的这一句
            if n > 0, let s = entry.sections.first(where: { $0.blocks.contains { b in
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

    @ViewBuilder private func buttons(_ r: Outcome) -> some View {
        HStack(spacing: 12) {
            if !r.final {
                Button("下一步:打字 ›") { begin(step: 2) }.buttonStyle(LedgeButtonStyle())
            } else {
                if !r.ok && !r.draft.isEmpty {
                    // 算我对: tense, contractions, synonyms the Chinese can't tell apart
                    Button("算我对") { model.markLastRight(entry); result = Outcome(diff: r.diff, draft: r.draft, ok: true, steps: r.steps, final: true); autoMove() }
                        .buttonStyle(LedgeButtonStyle(outline: true))
                }
                Button("再试一次") { fresh() }
                    .buttonStyle(LedgeButtonStyle(outline: true))
                if let s = stage, s == .test, model.advanceReady(entry), let nx = s.next {
                    Button("移到\(nx.short)") { model.move(entry, to: nx); onNextEntry?() }.buttonStyle(LedgeButtonStyle())
                }
                Spacer()
                if let nx = nextUndone() {
                    Button("下一句 ›") { pos = nx; start(keepDone: true) }.buttonStyle(LedgeButtonStyle())
                } else if let onNextEntry {
                    Button("下一条 ›", action: onNextEntry).buttonStyle(LedgeButtonStyle())
                } else if let onNotes {
                    Button("回到笔记", action: onNotes).buttonStyle(LedgeButtonStyle())
                }
            }
        }
    }

    // MARK: flow

    private func start() { start(keepDone: false) }

    private func start(keepDone: Bool) {
        if !keepDone {
            done = []; order = nil
            // in note order, or for a non-essay the (attempts % n)th sentence, as the page does
            pos = entry.isEssay || n == 0 ? 0 : model.attempts(entry).count % n
        }
        fresh()
    }

    /// A sentence from the top: ① 选词, or straight to ② when it was answered right before.
    private func fresh() {
        let skip = !entry.isEssay && model.rightBefore(entry, key)
        begin(step: skip ? 2 : 1, skip: skip)
    }

    /// [s] 1 = ① 选词, 2 = ② 打字; [skip] when ① was skipped because the sentence was right before.
    private func begin(step s: Int, skip: Bool = false) {
        skipped = skip
        if s == 1 { bankOk = false }
        step = s
        chips = s == 1
        picked = []; typed = ""
        fills = Array(repeating: "", count: blanks?.filter(\.key).count ?? 0)
        revealed = false; result = nil; note = nil
        if s == 2 { DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { focus = 0 } }
    }

    private func shuffle() {
        if order == nil { order = dealOrder(n, first: k); pos = 0 } else { pos = k; order = nil }
        done = []
        start(keepDone: true)
    }

    /// The next sentence not yet answered since the entry was opened.
    private func nextUndone() -> Int? {
        guard n > 1 else { return nil }
        for d in 1..<n {
            let p = pos + d
            // an essay goes through once, in order
            if entry.isEssay && p >= n { return nil }
            if !done.contains(order.map { $0[p % n] } ?? p % n) { return p }
        }
        return nil
    }

    private func reveal() {
        guard let q, !revealed else { return }
        focus = nil
        let text = draft
        let d = bestDiff(text, q.en)
        let right = d.same && !text.isEmpty
        if step == 1 && !entry.isEssay {
            bankOk = right
            result = Outcome(diff: d, draft: text, ok: right, steps: nil, final: false)
        } else {
            let helped = step == 2 && chips
            let typedOk = right && !helped
            let ok = entry.isEssay ? right : typedOk && (skipped || bankOk)
            let typedMark = typedOk ? "✓" : helped ? "✗(用选词交的)" : "✗"
            let steps: String? = entry.isEssay ? nil : skipped
                ? "打字 \(typedMark) — 这一句以前答对过,跳过了选词;" + (typedOk ? "这一句算答对" : "这次记为没对")
                : "选词 \(bankOk ? "✓" : "✗") · 打字 \(typedMark) — " + (bankOk && typedOk ? "两步都对,这一句算答对" : "两步都对才算答对,这次记为没对")
            model.record(entry, Attempt(t: nowMs(), text: text, ok: ok, via: "entry", ex: key))
            done.insert(q.k)
            result = Outcome(diff: d, draft: text, ok: ok, steps: steps, final: true)
            autoMove()
        }
        withAnimation(.snappy) { revealed = true }
    }

    /// 学习中 → 复习 by itself once every sentence has been right (用户 2026-11-01).
    private func autoMove() {
        if stage == .learn, model.advanceReady(entry) {
            model.move(entry, to: .review)
            note = "这一阶段的题都答对过了,已移到复习"
        }
    }
}

/// An entry with no sentence to check against: write, then grade it myself.
private struct SelfGraded: View {
    let entry: Entry
    @Environment(AppModel.self) private var model
    @State private var text = ""
    @State private var shown = false
    @State private var graded: Bool?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("这条没有例句可以对照:先写,再看笔记自己判断。").font(Theme.font(17, .bold)).foregroundStyle(Theme.ink2)
            TextField("写下你记得的", text: $text, axis: .vertical).font(Theme.font(20, .semibold)).lineLimit(2...8)
                .padding(14).ledgeCard()
            if !shown {
                Button("显示答案") { shown = true }.buttonStyle(LedgeButtonStyle(face: Theme.blue, edge: Theme.blueEdge))
            } else if let g = graded {
                Text(g ? "已记为 ✅" : "已记为 ❌").font(Theme.font(17, .heavy))
            } else {
                HStack {
                    Button("我写对了") { grade(true) }.buttonStyle(LedgeButtonStyle())
                    Button("没写对") { grade(false) }.buttonStyle(LedgeButtonStyle(outline: true))
                }
            }
            Spacer()
        }.padding(20)
    }

    private func grade(_ ok: Bool) {
        graded = ok
        model.record(entry, Attempt(t: nowMs(), text: text, ok: ok, via: "entry"))
    }
}
