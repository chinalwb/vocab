import SwiftUI

/*
 * The entry's 自测, the back of its card: one sentence at a time on the shared AnswerSheet (① 选词 →
 * ② 打字). In 学习中 / 复习 ② types only the **bold** key part (fillBlanks), in 考核 the whole sentence.
 * An essay asks 选词 only, in note order. Once every sentence has been right since the entry entered
 * 学习中, it moves to 复习 by itself. The entry page never grades SM-2 — 复习这一条 / the runs do.
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
    @State private var round = 0
    @State private var note: String?
    @State private var begun = false

    private var stage: Stage? { model.stage(entry) }
    private var xs: [Example] { model.examples(entry) }
    private var n: Int { xs.count }
    private var selfTest: Bool { entry.level == "SELFTEST" }

    private var k: Int { n == 0 ? 0 : order.map { $0[pos % n] } ?? pos % n }
    private var q: Question? {
        if n > 0 { let x = xs[k]; return Question(en: plain(x.en), zh: plain(x.zh), raw: x.en, k: k, of: n) }
        if selfTest { let a = entry.selfTestAnswer; return Question(en: a, zh: entry.title, raw: a, k: 0, of: 1) }
        return nil
    }
    private var key: String { selfTest ? "" : q?.en ?? "" }

    var body: some View {
        if let q {
            AnswerSheet(entry: entry, q: q, key: key, fill: stage != .test && !entry.isEssay && !selfTest,
                        others: xs.map { plain($0.en) }, onAnswer: answered) {
                if let onNotes {
                    Button(action: onNotes) { Label("看笔记", systemImage: "chevron.left").font(Theme.font(15, .bold)) }
                        .buttonStyle(.plain).foregroundStyle(Theme.ink2)
                }
                Text(n > 0 ? "例句 \(q.k + 1) / \(q.of)" : "自测题").font(Theme.font(15, .heavy)).foregroundStyle(Theme.ink2)
            } trail: {
                if n > 1 && !entry.isEssay { ShufflePill(on: order != nil, act: shuffle) }
            } after: { r, markRight in
                buttons(r, markRight)
            }
            .id("\(k)/\(round)")
            .onAppear {
                guard !begun else { return }
                begun = true
                // in note order, or for a non-essay the (attempts % n)th sentence, as the page does
                pos = entry.isEssay || n == 0 ? 0 : model.attempts(entry).count % n
            }
        } else {
            SelfGraded(entry: entry)
        }
    }

    @ViewBuilder private func buttons(_ r: SheetOutcome, _ markRight: @escaping () -> Void) -> some View {
        if let note { Text(note).font(Theme.font(15, .bold)).foregroundStyle(Theme.okInk) }
        HStack(spacing: 12) {
            if !r.ok && !r.draft.isEmpty {
                // 算我对: tense, contractions, synonyms the Chinese can't tell apart
                Button("算我对") { model.markLastRight(entry); markRight(); autoMove() }
                    .buttonStyle(LedgeButtonStyle(outline: true))
            }
            Button("再试一次") { note = nil; round += 1 }
                .buttonStyle(LedgeButtonStyle(outline: true))
            if let s = stage, s != .learn, model.advanceReady(entry), let nx = s.next {
                Button("移到\(nx.short)") { model.move(entry, to: nx); onNextEntry?() }.buttonStyle(LedgeButtonStyle())
            }
            Spacer()
            if let nx = nextUndone() {
                Button("下一句 ›") { note = nil; pos = nx }.buttonStyle(LedgeButtonStyle())
            } else if let onNextEntry {
                Button("下一条 ›", action: onNextEntry).buttonStyle(LedgeButtonStyle())
            } else if let onNotes {
                Button("回到笔记", action: onNotes).buttonStyle(LedgeButtonStyle())
            }
        }
    }

    private func answered(_ text: String, _ ok: Bool) {
        model.record(entry, Attempt(t: nowMs(), text: text, ok: ok, via: "entry", ex: key))
        done.insert(k)
        autoMove()
    }

    /// 打乱: a random order that starts on another sentence; off again, back to note order here.
    private func shuffle() {
        if order == nil { order = dealOrder(n, first: k); pos = 0 } else { pos = k; order = nil }
        done = []; note = nil; round += 1
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

    /// 学习中 → 复习 by itself once every sentence has been right (用户 2026-11-01).
    private func autoMove() {
        if stage == .learn, model.advanceReady(entry) {
            model.move(entry, to: .review)
            note = "这一阶段的题都答对过了,已移到复习"
        }
    }
}

/// 打乱: off = note order, on = shuffled.
struct ShufflePill: View {
    let on: Bool
    let act: () -> Void
    var body: some View {
        Button(action: act) {
            Label("打乱", systemImage: "shuffle").font(Theme.font(14, .bold))
                .padding(.horizontal, 12).padding(.vertical, 6)
                .foregroundStyle(on ? Theme.ground : Theme.ink2)
                .background(Capsule().fill(on ? Theme.ink : Theme.soft))
        }.buttonStyle(.plain)
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
