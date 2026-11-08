import SwiftUI

/*
 * A 复习 / 考核 run, full screen: ✕ · the progress bar · 3 / 12 on top. 复习 shows the notes first (先看一遍
 * → 翻面做题); then up to three of the entry's sentences (runSentences), each ① 选词 → ② 打字, and the
 * headword stays hidden until the reveal. After the entry's last sentence the whole entry is graded once:
 * 复习 grades its SM-2 card (all right = 记得, else 忘了) and moves it to 考核 by itself on 下一条 when it's
 * earned; a miss in 考核 goes back to 复习. The 完成 page lists those moves with one 撤销移动.
 * Same rules as renderQuizCard() / renderStageTab() on the page and QuizScreen.kt.
 */
struct RunView: View {
    let run: RunSession
    @Environment(AppModel.self) private var model
    @Environment(\.accessibilityReduceMotion) private var still

    var body: some View {
        VStack(spacing: 0) {
            topBar
            ZStack {
                if let a = run.anchor, let e = model.entry(a) {
                    RunEntry(run: run, entry: e, next: { adv in next(e, adv) })
                        .id(run.index)
                        .transition(still ? .opacity : .asymmetric(insertion: .move(edge: .trailing), removal: .move(edge: .leading)))
                } else {
                    finished.transition(.opacity)
                }
            }
            .frame(maxWidth: 900, maxHeight: .infinity)
            .clipped()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Theme.ground)
    }

    private var topBar: some View {
        HStack(spacing: 16) {
            Button { model.run = nil } label: {
                Image(systemName: "xmark").font(.system(size: 20, weight: .bold)).frame(width: 44, height: 44)
            }.buttonStyle(.plain).foregroundStyle(Theme.ink3).accessibilityLabel("结束这一轮")
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(Theme.rule)
                    Capsule().fill(Theme.go).frame(width: g.size.width * CGFloat(run.index) / CGFloat(max(run.queue.count, 1)))
                }
            }
            .frame(height: 16)
            .animation(.snappy, value: run.index)
            Text("\(min(run.index + 1, run.queue.count)) / \(run.queue.count)").font(Theme.font(17, .heavy)).foregroundStyle(Theme.ink3)
                .monospacedDigit()
        }
        .padding(.horizontal, 20).padding(.vertical, 10)
    }

    /// 下一条: the move I tapped ([adv]), or the one the run makes by itself.
    private func next(_ e: Entry, _ adv: Bool) {
        let ready = model.advanceReady(e)
        let auto = ready && run.stage == .review, back = run.stage == .test && run.missed && !ready
        if adv || auto, let nx = run.stage.next {
            model.move(e, to: nx); run.advanced += 1
            if run.stage == .review { run.promoted.append(e.anchor) }
        } else if back {
            model.move(e, to: .review); model.grade(e, ok: false); run.demoted.append(e.anchor)
        }
        withAnimation(.easeInOut(duration: 0.3)) { run.nextEntry() }
    }

    private var finished: some View {
        let names = { (xs: [String]) in xs.map { model.entry($0)?.title ?? $0 }.joined(separator: "、") }
        return VStack(alignment: .leading, spacing: 18) {
            Text("完成 🎉").font(Theme.font(40, .black)).foregroundStyle(Theme.ink)
            Text("这一轮做了 \(run.done) 条" + (run.checked > 0 ? ",答对 \(run.ok) / \(run.checked) 句" : "")
                 + (run.advanced > 0 ? ",\(run.advanced) 条进入了\(run.stage.next?.short ?? "")" : "") + "。")
                .font(Theme.font(19, .semibold)).foregroundStyle(Theme.ink2)
            if !run.promoted.isEmpty {
                Text("自动移到考核(复习里连续答对 \(promoteReps) 次):\(names(run.promoted))").font(Theme.font(17, .semibold)).foregroundStyle(Theme.okInk)
            }
            if !run.demoted.isEmpty {
                Text("答错了,退回复习(排期从明天重新开始):\(names(run.demoted))").font(Theme.font(17, .semibold)).foregroundStyle(Theme.badInk)
            }
            HStack(spacing: 12) {
                Button("返回") { model.run = nil }.buttonStyle(LedgeButtonStyle())
                if !run.promoted.isEmpty || !run.demoted.isEmpty {
                    // the schedule stays as graded
                    Button("撤销移动") {
                        for a in run.promoted { if let e = model.entry(a) { model.move(e, to: .review) } }
                        for a in run.demoted { if let e = model.entry(a) { model.move(e, to: .test) } }
                        run.advanced -= run.promoted.count; run.promoted = []; run.demoted = []
                    }.buttonStyle(LedgeButtonStyle(outline: true))
                }
            }
            Spacer()
        }
        .padding(32).frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// One entry of a run: 先看一遍 (复习), then its sentences.
private struct RunEntry: View {
    let run: RunSession
    let entry: Entry
    let next: (_ adv: Bool) -> Void
    @Environment(AppModel.self) private var model

    private var selfTest: Bool { entry.level == "SELFTEST" }
    private var stage: Stage { run.stage }

    var body: some View {
        // a ZStack with a clear floor: an empty Group would never appear, so prepare() would never run
        ZStack {
            Color.clear
            if stage == .review && !run.studied {
                study
            } else if let q = question {
                sheet(q)
            } else if run.qs != nil || selfTest {
                RunSelfGraded(run: run, entry: entry, next: next)
            }
        }
        .onAppear(perform: prepare)
        // 不再考这句 mid-run: a sentence still to come leaves this run at once; the one on screen keeps going
        .onChange(of: model.quizOut[entry.anchor]) {
            guard let qs = run.qs, run.k + 1 < qs.count else { return }
            run.qs = Array(qs[...run.k]) + qs[(run.k + 1)...].filter { !model.isOut(entry, $0) }
        }
    }

    /// Once per entry: what it asks, and the visit for 学习历史.
    private func prepare() {
        guard run.qs == nil else { return }
        var qs = selfTest ? [] : model.runSentences(entry)
        if run.shuffle { qs.shuffle() }
        run.qs = qs
        model.visit(entry, via: stage.rawValue)
    }

    // MARK: 先看一遍

    private var study: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("\(groupLabel(entry.level)) · 先看一遍").font(Theme.font(15, .heavy)).foregroundStyle(Theme.ink3)
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text(entry.title).font(Theme.font(30, .heavy)).foregroundStyle(Theme.ink)
                if !entry.speakText.isEmpty { SpeakButton(text: entry.speakText, size: 20) }
            }
            NotesCard(entry: entry, extras: [.note]) { EmptyView() } footer: {
                HStack {
                    Spacer()
                    Button("翻面做题") { withAnimation(.easeInOut(duration: 0.35)) { run.studied = true } }.buttonStyle(LedgeButtonStyle())
                }.padding(.horizontal, 16).padding(.top, 10).padding(.bottom, 8)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .ledgeCard(Theme.wash(stage), radius: 22)
        }
        .padding(.horizontal, 24).padding(.bottom, 20)
        .transition(.asymmetric(insertion: .opacity, removal: .opacity.combined(with: .scale(scale: 0.97))))
    }

    // MARK: the sentences

    private var qs: [String] { run.qs ?? [] }
    private var n: Int { selfTest ? 1 : qs.count }
    private var lastQ: Bool { run.k + 1 >= n }

    private var question: Question? {
        if selfTest { let a = entry.selfTestAnswer; return a.isEmpty ? nil : Question(en: a, zh: entry.title, raw: a, k: 0, of: 1) }
        guard run.k < qs.count else { return nil }
        let xs = entry.quizExamples
        guard let k = xs.firstIndex(where: { plain($0.en) == qs[run.k] }) else { return nil }
        return Question(en: qs[run.k], zh: plain(xs[k].zh), raw: xs[k].en, k: k, of: xs.count)
    }

    private func sheet(_ q: Question) -> some View {
        let key = selfTest ? "" : q.en
        return AnswerSheet(entry: entry, q: q, key: key, fill: stage == .review && !selfTest,
                           others: model.examples(entry).map { plain($0.en) }, onAnswer: { answered(key, $0, $1) }) {
            Text(selfTest ? "自测题" : "第 \(run.k + 1) / \(n) 句").font(Theme.font(15, .heavy)).foregroundStyle(Theme.ink2)
            if n > 1 { SentenceBars(k: run.k, n: n) }
        } trail: {
            if !selfTest && n - run.k > 1 { ShufflePill(on: run.shuffle, act: shuffle) }
        } after: { r, markRight in
            after(r, markRight)
        }
        .id("\(run.k)/\(run.round)")
        .transition(.opacity)
    }

    private func answered(_ key: String, _ text: String, _ ok: Bool) {
        model.record(entry, Attempt(t: nowMs(), text: text, ok: ok, via: stage.rawValue, ex: key))
        if !text.isEmpty { run.checked += 1; if ok { run.ok += 1 } }
        run.entryOkBefore = run.entryOk
        run.cardBefore = model.card(entry)
        run.entryOk = run.entryOk && ok
        if lastQ {
            if stage == .review { model.grade(entry, ok: run.entryOk) }
            run.missed = !run.entryOk
        }
    }

    /// 打乱 is the run's: on, the sentences still to come are dealt again (and the next entries' too);
    /// off, they go back to note order. Either way this sentence starts over.
    private func shuffle() {
        run.shuffle.toggle()
        let order = model.examples(entry).map { plain($0.en) }
        let rest = Array(qs[run.k...])
        let kept = run.shuffle ? dealOrder(rest.count, first: 0).map { rest[$0] }
            : rest.sorted { (order.firstIndex(of: $0) ?? 0) < (order.firstIndex(of: $1) ?? 0) }
        run.qs = Array(qs[..<run.k]) + kept
        run.round += 1
    }

    @ViewBuilder private func after(_ r: SheetOutcome, _ markRight: @escaping () -> Void) -> some View {
        let final = lastQ
        let ready = final && model.advanceReady(entry)
        let auto = ready && stage == .review, back = final && stage == .test && run.missed && !ready
        // the headword, hidden until now: it is often the very answer
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(entry.title).font(Theme.font(22, .heavy)).foregroundStyle(Theme.ink)
            if !entry.speakText.isEmpty { SpeakButton(text: entry.speakText) }
        }
        if final && n > 1 {
            note("这次的 \(n) 句都做完了:" + (run.entryOk ? "全部答对" : "有没答对的,整条记为没对"), ok: run.entryOk)
        }
        if auto { note("复习里连续答对 \(promoteReps) 次了,下一条时自动移到考核", ok: true) }
        else if back { note("没答对,下一条时退回复习,排期从明天重新开始", ok: false) }
        HStack(spacing: 12) {
            if !r.ok && !r.draft.isEmpty {
                // 算我对: this sentence was right after all — on the last one the SM-2 grade and the
                // 考核 miss are worked out again
                Button("算我对") {
                    model.markLastRight(entry)
                    run.ok += 1; run.entryOk = run.entryOkBefore
                    if lastQ {
                        if stage == .review { model.restoreCard(entry, run.cardBefore); model.grade(entry, ok: run.entryOk) }
                        run.missed = !run.entryOk
                    }
                    markRight()
                }.buttonStyle(LedgeButtonStyle(outline: true))
            }
            Spacer()
            if !final {
                Button("下一句 › \(run.k + 2) / \(n)") { withAnimation(.easeInOut(duration: 0.2)) { run.k += 1 } }.buttonStyle(LedgeButtonStyle())
            } else {
                if ready && !auto, let nx = stage.next {
                    Button("移到\(nx.short)") { next(true) }.buttonStyle(LedgeButtonStyle(outline: true))
                }
                Button(run.index + 1 < run.queue.count ? "下一条 ›" : "完成") { next(false) }.buttonStyle(LedgeButtonStyle())
            }
        }
    }

    private func note(_ t: String, ok: Bool) -> some View {
        Text(t).font(Theme.font(15, .bold)).foregroundStyle(ok ? Theme.okInk : Theme.badInk)
            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 12).fill(ok ? Theme.okBg : Theme.badBg))
    }
}

/// The entry's sentences this run as short bars: done, this one, still to come.
struct SentenceBars: View {
    let k: Int, n: Int
    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<n, id: \.self) { j in
                Capsule().fill(j < k ? Theme.go : j == k ? Theme.selInk : Theme.rule).frame(width: 18, height: 6)
            }
        }
    }
}

/// An entry with nothing to check against, in a run: recall it from the masked gloss, look, grade it myself.
private struct RunSelfGraded: View {
    let run: RunSession
    let entry: Entry
    let next: (_ adv: Bool) -> Void
    @Environment(AppModel.self) private var model
    @State private var text = ""
    @State private var shown = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("回想这一条:先写,再看笔记自己判断").font(Theme.font(15, .heavy)).foregroundStyle(Theme.ink3)
                Text(shown ? entry.title : maskTitle(plain(entry.gloss), entry.title)).font(Theme.font(24, .bold)).foregroundStyle(Theme.ink)
                TextField("写下你记得的", text: $text, axis: .vertical).font(Theme.font(20, .semibold)).lineLimit(2...8)
                    .padding(14).ledgeCard()
                if !shown {
                    Button("显示答案") { withAnimation { shown = true } }.buttonStyle(LedgeButtonStyle(face: Theme.blue, edge: Theme.blueEdge))
                } else {
                    BlocksView(blocks: entry.blocks).padding(18).ledgeCard(Theme.wash(run.stage))
                    if let g = run.graded {
                        Text(g ? "已记为 ✅" : "已记为 ❌").font(Theme.font(17, .heavy))
                        HStack {
                            Spacer()
                            if model.advanceReady(entry) && run.stage != .review, let nx = run.stage.next {
                                Button("移到\(nx.short)") { next(true) }.buttonStyle(LedgeButtonStyle(outline: true))
                            }
                            Button(run.index + 1 < run.queue.count ? "下一条 ›" : "完成") { next(false) }.buttonStyle(LedgeButtonStyle())
                        }
                    } else {
                        HStack {
                            Button("我写对了") { grade(true) }.buttonStyle(LedgeButtonStyle())
                            Button("没写对") { grade(false) }.buttonStyle(LedgeButtonStyle(outline: true))
                        }
                    }
                }
            }.padding(20)
        }
    }

    private func grade(_ ok: Bool) {
        run.graded = ok
        model.record(entry, Attempt(t: nowMs(), text: text, ok: ok, via: run.stage.rawValue))
        if run.stage == .review { model.grade(entry, ok: ok) }
        run.checked += 1; if ok { run.ok += 1 }
        run.missed = !ok
    }
}

/// A 中→英 prompt must not give the answer away: the headword's English words blanked out.
func maskTitle(_ text: String, _ title: String) -> String {
    let ws = Set((title.matches(of: /[A-Za-z]{3,}/).map { String($0.output).lowercased() }))
    return ws.reduce(text) { t, w in t.replacingOccurrences(of: "\\b\(w)\\w*", with: "＿＿", options: [.regularExpression, .caseInsensitive]) }
}
