import SwiftUI

/// An independent training session; no stage track, word chips or ordinary self-test grades.
struct SpecialPracticeView: View {
    let entry: Entry
    let onXref: (String) -> Void
    @Environment(AppModel.self) private var model
    @State private var showingRun = false
    @State private var skill = ""
    @State private var blankAnswer = false
    @FocusState private var writing: Bool

    private var record: SpecialPracticeRecord { model.special(entry) }
    private func change(_ update: (inout SpecialPracticeRecord) -> Void) { model.updateSpecial(entry, update) }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("专项练习").font(Theme.font(14, .heavy)).foregroundStyle(Theme.brand)
                    Text(entry.title).font(Theme.font(28, .heavy)).foregroundStyle(Theme.ink)
                    if let set = entry.practice {
                        if showingRun, let run = record.currentRun {
                            if run.completed { results(set, run) }
                            else if let id = run.questionID, let q = set.questions.first(where: { $0.id == id }) {
                                question(set, run, q)
                            } else {
                                Text("题库已经更新，请返回首页重新开始。").foregroundStyle(Theme.ink2)
                                Button("返回练习首页") { showingRun = false }.buttonStyle(LedgeButtonStyle())
                            }
                        } else { home(set) }
                    } else {
                        ContentUnavailableView("练习尚未下载", systemImage: "arrow.clockwise", description: Text("返回列表，重新打开 App 更新题库。"))
                    }
                }
                .frame(maxWidth: 760, alignment: .leading)
                .padding(24).frame(maxWidth: .infinity, alignment: .topLeading)
            }
            .background(Theme.ground)
            .onChange(of: record.attempts.count) { writing = false; blankAnswer = false; withAnimation { proxy.scrollTo("feedback", anchor: .top) } }
        }
        .onAppear { model.visit(entry, via: "entry") }
        .onDisappear { Speaker.shared.stop() }
    }

    private func start(_ set: SpecialPracticeSet, _ mode: SpecialPracticeMode, skill: String = "") {
        change { _ = $0.start(set, mode: mode, skill: skill) }
        blankAnswer = false; writing = false; showingRun = true
    }

    @ViewBuilder private func home(_ set: SpecialPracticeSet) -> some View {
        let summary = record.summary(set)
        Text(set.description).font(Theme.font(17)).foregroundStyle(Theme.ink2)
        metrics([(value: "\(summary.done) / \(set.questions.count)", label: "已作答题目", id: "practice-done"),
                 (value: "\(summary.firstRight)", label: "首次独立答对", id: "practice-first-right"),
                 (value: "\(summary.review.count)", label: "需要重练", id: "practice-review-count")])
        Text(summary.done == 0 ? "尚未开始，一次练一点。" : summary.done == set.questions.count ? "整组题目已作答；完成不等于已经掌握，隔几天可以再练。" : "练习进行中，随时可以停下来。")
            .font(Theme.font(15)).foregroundStyle(Theme.ink2)
        if let run = record.currentRun, !run.completed {
            VStack(alignment: .leading, spacing: 12) {
                Text("上次做到第 \(run.index + 1) / \(run.order.count) 题").font(Theme.font(16, .bold))
                Button("继续练习 →") { showingRun = true }.buttonStyle(LedgeButtonStyle()).accessibilityIdentifier("practice-continue")
            }.padding(18).frame(maxWidth: .infinity, alignment: .leading).ledgeCard(Theme.soft)
        }
        VStack(alignment: .leading, spacing: 16) {
            Text("选择怎么练").font(Theme.font(20, .heavy))
            Picker("分项练习", selection: $skill) {
                Text("全部四个知识点").tag("")
                ForEach(set.orderedSkills, id: \.self) { key in Text(set.skills[key] ?? key).tag(key) }
            }.accessibilityIdentifier("practice-skill")
            Button("开始分项练习") { start(set, .focused, skill: skill) }
                .buttonStyle(LedgeButtonStyle()).accessibilityIdentifier("practice-start-focused")
            Button("开始混合练习 · \(set.questions.count) 题") { start(set, .mixed) }
                .buttonStyle(LedgeButtonStyle(face: Theme.blue, edge: Theme.blueEdge)).accessibilityIdentifier("practice-start-mixed")
            Button("重做错题与提示题 · \(summary.review.count)") { start(set, .review) }
                .buttonStyle(LedgeButtonStyle(outline: true)).disabled(summary.review.isEmpty).accessibilityIdentifier("practice-start-review")
        }.padding(18).frame(maxWidth: .infinity, alignment: .leading).ledgeCard()
        DisclosureGroup("先看核心笔记") {
            ForEach(Array(Set(set.questions.map(\.note))).sorted(), id: \.self) { anchor in
                Button(model.entry(anchor)?.title ?? anchor) { onXref(anchor) }.font(Theme.font(16, .bold)).padding(.vertical, 6)
            }
        }.font(Theme.font(17, .bold))
        DisclosureGroup("练习记录与未完成的练习") {
            if record.runs.isEmpty { Text("还没有练习记录。").font(Theme.font(15)).foregroundStyle(Theme.ink3) }
            ForEach(record.runs.values.sorted { $0.started > $1.started }) { run in
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(run.mode.label) · \(run.submissions.count) / \(run.order.count) 题").font(Theme.font(16, .bold))
                        Text(Date(timeIntervalSince1970: Double(run.started) / 1000).formatted(date: .abbreviated, time: .shortened))
                            .font(Theme.font(13)).foregroundStyle(Theme.ink3)
                    }
                    Spacer()
                    Button(run.completed ? "查看结果" : "继续") { change { $0.current = run.id }; showingRun = true }
                }.padding(.vertical, 8)
            }
        }.font(Theme.font(17, .bold))
        storageNote
    }

    @ViewBuilder private func question(_ set: SpecialPracticeSet, _ run: SpecialPracticeRun, _ q: SpecialPracticeQuestion) -> some View {
        let attempt = record.attempt(run, question: q.id)
        HStack {
            Text("第 \(run.index + 1) / \(run.order.count) 题 · \(q.type.label)").font(Theme.font(14, .bold)).accessibilityIdentifier("practice-position")
            Spacer()
            Button("保存并返回首页") { writing = false; showingRun = false }.font(Theme.font(14, .bold)).accessibilityIdentifier("practice-home")
        }
        ProgressView(value: Double(run.submissions.count), total: Double(run.order.count)).tint(Theme.go)
        if run.mode == .focused {
            Text(set.skills[q.skill] ?? q.skill).font(Theme.font(13, .bold)).foregroundStyle(Theme.selInk)
                .padding(8).background(Capsule().fill(Theme.selBg))
        }
        Text(q.prompt).font(Theme.font(21, .bold)).foregroundStyle(Theme.ink).textSelection(.enabled)
        Text(q.type == .fill ? "填写空缺部分" : "写出完整句子").font(Theme.font(16, .bold))
        if let attempt {
            Text(attempt.answer).font(Theme.font(18)).textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading).padding(16).ledgeCard(Theme.soft)
        } else {
            TextEditor(text: Binding(get: { model.special(entry).currentRun?.drafts[q.id] ?? "" }, set: { value in
                change { $0.saveDraft(value) }; blankAnswer = false
            }))
            .font(Theme.font(18)).scrollContentBackground(.hidden).frame(minHeight: 110, maxHeight: 180)
            .padding(12).background(Theme.ground).clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.rule, lineWidth: 2))
            .focused($writing).textInputAutocapitalization(.sentences).autocorrectionDisabled()
            .accessibilityLabel("你的答案").accessibilityIdentifier("practice-answer").id(q.id)
            if blankAnswer { Text("先写下你的答案，再提交。").font(Theme.font(15, .bold)).foregroundStyle(Theme.badInk) }
            HStack(spacing: 18) {
                Button("提交答案") { submit(set) }.buttonStyle(LedgeButtonStyle()).keyboardShortcut(.return, modifiers: .command)
                    .accessibilityIdentifier("practice-submit")
                Button(run.help[q.id] == true ? "已查看提示" : "需要一点提示") { change { $0.hint() } }
                    .font(Theme.font(15, .bold)).accessibilityIdentifier("practice-show-hint")
            }
            if run.help[q.id] == true {
                VStack(alignment: .leading, spacing: 12) {
                    Text(q.hint).font(Theme.font(17))
                    Button("打开对应核心笔记 →") { writing = false; onXref(q.note) }.font(Theme.font(15, .bold))
                }.padding(16).frame(maxWidth: .infinity, alignment: .leading).ledgeCard(Theme.selBg)
            }
        }
        if let attempt { feedback(set, run, q, attempt).id("feedback") }
        storageNote
    }

    private func submit(_ set: SpecialPracticeSet) {
        var accepted = false
        change { accepted = $0.submit(set) }
        blankAnswer = !accepted
        if accepted { writing = false }
    }

    private func feedback(_ set: SpecialPracticeSet, _ run: SpecialPracticeRun, _ q: SpecialPracticeQuestion, _ a: SpecialPracticeAttempt) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(a.ok ? (a.help ? "提示后答对了，再独立练一次" : "答对了！") : "这次还没写对，一起看一下")
                .font(Theme.font(21, .heavy)).foregroundStyle(a.ok ? Theme.okInk : Theme.missInk).accessibilityIdentifier("practice-verdict")
            Text(q.answers.count > 1 ? "参考答案（以下写法均可）" : "参考答案").font(Theme.font(13)).foregroundStyle(Theme.ink3)
            ForEach(q.answers, id: \.self) { answer in HStack(alignment: .top) {
                Text(answer).font(Theme.font(18, .bold)).textSelection(.enabled)
                SpeakButton(text: answer)
            } }
            Text(q.explanation).font(Theme.font(17))
            if !a.ok { DisclosureGroup("看看和参考答案差在哪里") {
                let diff = q.answers.map { diffWords(normalizedPracticeAnswer(a.answer), normalizedPracticeAnswer($0)) }
                    .min { $0.extra + $0.missing < $1.extra + $1.missing }!
                VStack(alignment: .leading, spacing: 10) {
                    Text("我的答案（划掉的是需要改的部分）").font(Theme.font(13))
                    Text(markWords(diff.mine, kept: diff.keepMine, wrong: true))
                    Text("参考答案（高亮的是缺少或需要改的部分）").font(Theme.font(13))
                    Text(markWords(diff.answer, kept: diff.keepAnswer, wrong: false))
                }.font(Theme.font(17)).padding(.top, 8)
            } }
            Button("看看对应核心笔记 →") { onXref(q.note) }.font(Theme.font(15, .bold))
            Button(run.index == run.order.count - 1 ? "查看本次结果" : "下一题 →") {
                change { $0.next() }; blankAnswer = false
            }.buttonStyle(LedgeButtonStyle()).accessibilityIdentifier("practice-next")
        }.padding(18).frame(maxWidth: .infinity, alignment: .leading).ledgeCard(a.ok ? Theme.okBg : Theme.missBg)
    }

    @ViewBuilder private func results(_ set: SpecialPracticeSet, _ run: SpecialPracticeRun) -> some View {
        let attempts = run.order.compactMap { record.attempt(run, question: $0) }
        Text("本次练习完成").font(Theme.font(23, .heavy)).accessibilityIdentifier("practice-completed")
        metrics([(value: "\(attempts.filter { $0.ok && !$0.help }.count)", label: "独立答对", id: "practice-run-right"),
                 (value: "\(attempts.filter { $0.ok && $0.help }.count)", label: "提示后答对", id: "practice-run-help"),
                 (value: "\(attempts.filter { !$0.ok }.count)", label: "需要订正", id: "practice-run-wrong")])
        Text("这次完成 \(attempts.count) 题。刚看完答案不等于已经掌握，隔几天再试。").font(Theme.font(17)).foregroundStyle(Theme.ink2)
        Button("重练错题与提示题") { start(set, .review) }.buttonStyle(LedgeButtonStyle())
            .disabled(record.summary(set).review.isEmpty)
        Button("返回练习首页") { showingRun = false }.buttonStyle(LedgeButtonStyle(outline: true)).accessibilityIdentifier("practice-results-home")
        ForEach(Array(attempts.enumerated()), id: \.element.id) { index, a in
            if let q = set.questions.first(where: { $0.id == a.q }) {
                DisclosureGroup("第 \(index + 1) 题 · \(a.ok ? (a.help ? "提示后答对" : "独立答对") : "需要订正")") {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(q.prompt); Text("我的答案：\(a.answer)"); Text("参考答案：\(q.answers[0])"); Text(q.explanation)
                    }.font(Theme.font(16)).padding(.vertical, 10).textSelection(.enabled)
                }.font(Theme.font(17, .bold))
            }
        }
        storageNote
    }

    private var storageNote: some View {
        Text("进度、草稿和答题记录自动保存在这台 iPad；专项练习暂不与网页同步。提示后答对也会列入重练，不会直接算作掌握。")
            .font(Theme.font(13)).foregroundStyle(Theme.ink3)
    }
    private func metrics(_ values: [(value: String, label: String, id: String)]) -> some View {
        HStack(alignment: .top, spacing: 10) {
            ForEach(values.indices, id: \.self) { i in VStack(spacing: 8) {
                Text(values[i].value).font(Theme.font(25, .heavy)).accessibilityIdentifier(values[i].id)
                Text(values[i].label).font(Theme.font(12, .bold)).foregroundStyle(Theme.ink3)
            }.frame(maxWidth: .infinity).padding(.vertical, 14).ledgeCard(Theme.soft) }
        }
    }
    private func markWords(_ words: [String], kept: Set<Int>, wrong: Bool) -> AttributedString {
        words.enumerated().reduce(into: AttributedString()) { result, word in
            if word.offset > 0 { result += AttributedString(" ") }
            var part = AttributedString(word.element)
            if !kept.contains(word.offset) {
                part.foregroundColor = wrong ? Theme.badInk : Theme.missInk
                if wrong { part.strikethroughStyle = .single } else { part.backgroundColor = Theme.missBg }
            }
            result += part
        }
    }
}
