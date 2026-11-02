import SwiftUI

/// What the sidebar picks: everything, one stage, or one group.
enum Pick: Hashable {
    case all
    case stage(Stage)
    case group(String)
}

/// Three columns like the web's desk layout: sidebar · entry list · the entry.
struct RootView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var phase
    @State private var pick: Pick? = .all
    @State private var selected: String?
    @State private var query = ""
    @State private var settings = false
    @State private var columns = NavigationSplitViewVisibility.all
    /// Cross-references opened from the entry, so ← 返回 can walk back.
    @State private var trail: [String] = []

    /// 复习 / 考核 grouped by why each entry is there, in the order a run takes them.
    private var groups: [(title: String, entries: [Entry])]? {
        guard query.trimmingCharacters(in: .whitespaces).isEmpty, case .stage(let s) = pick ?? .all, s == .review || s == .test else { return nil }
        if s == .review {
            let p = model.reviewPlan(), planned = Set((p.due + p.fresh).map(\.anchor))
            let later = model.inStage(.review).filter { !planned.contains($0.anchor) }
                .sorted { (model.card($0)?.due ?? .max) < (model.card($1)?.due ?? .max) }
            return [("今天到期", p.due), ("新加入", p.fresh), ("以后到期", later)].filter { !$0.1.isEmpty }
        }
        let q = model.quizQueue(.test)
        return [(0, "没做过"), (1, "上次没对"), (2, "上次答对")].map { g, t in (t, q.filter { model.lastGroup($0) == g }) }.filter { !$0.1.isEmpty }
    }

    private var list: [Entry] {
        if let groups { return groups.flatMap(\.entries) }
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        let base = model.entries.filter { e in
            switch pick ?? .all {
            case .all: return true
            case .stage(let s): return model.stage(e) == s
            case .group(let g): return e.level == g
            }
        }
        guard !q.isEmpty else { return base }
        // #12 or 12 finds entry 12; anything else searches the notes (all entries, not just this list)
        let num = Int(q.hasPrefix("#") ? String(q.dropFirst()) : q)
        return model.entries.filter { e in num.map { e.no == $0 } ?? e.searchText.contains(q) }
    }

    var body: some View {
        NavigationSplitView(columnVisibility: $columns) {
            sidebar
        } content: {
            entryList
        } detail: {
            NavigationStack {
                if let a = trail.last ?? selected, let e = model.entry(a) {
                    EntryView(entry: e, place: place(of: selected), onNext: next, onXref: { trail.append($0) })
                        .id(e.anchor)
                        .toolbar {
                            if !trail.isEmpty {
                                ToolbarItem(placement: .topBarLeading) {
                                    Button { trail.removeLast() } label: { Label("返回", systemImage: "chevron.left") }
                                }
                            }
                        }
                } else {
                    ContentUnavailableView("选一条笔记", systemImage: "book.closed", description: Text("左边的列表里点一条,就在这里打开"))
                }
            }
        }
        .tint(Theme.selInk)
        .task {
            // `-open <anchor>` at launch (simulator testing) opens that entry; `-quiz YES` flips to 自测;
            if let a = UserDefaults.standard.string(forKey: "open") { selected = a; columns = .detailOnly }
            // `-pick review` shows a stage's list; `-run review` starts its run (simulator testing too)
            if let p = UserDefaults.standard.string(forKey: "pick").flatMap(Stage.init) { pick = .stage(p) }
            if let r = UserDefaults.standard.string(forKey: "run").flatMap(Stage.init) {
                model.run = RunSession(stage: r, queue: model.quizQueue(r).map(\.anchor))
            }
            await model.refresh()
        }
        .task { await model.syncNow() }
        .onChange(of: phase) {
            // back in the app: pull what the page did; leaving it: push what's waiting
            if phase == .active { Task { await model.refresh(); await model.syncNow() } }
            if phase == .background { model.flush() }
        }
        .onChange(of: selected) { trail = [] }
        .sheet(isPresented: $settings) { SettingsView().environment(model) }
        .fullScreenCover(item: Binding(get: { model.run }, set: { model.run = $0 })) { RunView(run: $0).environment(model) }
    }

    private var sidebar: some View {
        List(selection: $pick) {
            Section {
                HStack(spacing: 12) {
                    if let img = UIImage(named: "avatar.jpg") ?? Bundle.main.path(forResource: "avatar", ofType: "jpg").flatMap(UIImage.init(contentsOfFile:)) {
                        Image(uiImage: img).resizable().scaledToFill().frame(width: 40, height: 40).clipShape(Circle())
                    }
                    Text("taobao").font(Theme.font(24, .black)).foregroundStyle(Theme.brand)
                }.padding(.vertical, 4)
            }
            Section("学习阶段") {
                row("全部条目", "square.grid.2x2", model.entries.count).tag(Pick.all)
                ForEach(Stage.allCases) { s in
                    Label { HStack { Text(s.label); Spacer(); count(model.entries.filter { model.stage($0) == s }.count) } } icon: {
                        Circle().fill(Theme.dot(s)).frame(width: 12, height: 12)
                    }.font(Theme.font(17, .bold)).tag(Pick.stage(s))
                }
            }
            Section("分类") {
                ForEach(groupNames.filter { g in model.entries.contains { $0.level == g.key } }, id: \.key) { g in
                    row(g.label, g.key == "ESSAY" ? "doc.richtext" : g.key == "COLLOC" ? "link" : "text.quote",
                        model.entries.filter { $0.level == g.key }.count).tag(Pick.group(g.key))
                }
            }
            Section {
                Button { settings = true } label: { Label("设置", systemImage: "gearshape").font(Theme.font(17, .bold)) }
                if let err = model.sync.error {
                    Text("同步出错:\(err)").font(Theme.font(14, .semibold)).foregroundStyle(Theme.badInk)
                        .onTapGesture { settings = true }
                }
                if let note = model.updateNote {
                    Text(note).font(Theme.font(14, .semibold)).foregroundStyle(Theme.okInk)
                        .onTapGesture { model.updateNote = nil }
                }
            }
        }
        .navigationTitle("VoCab")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func row(_ t: String, _ icon: String, _ n: Int) -> some View {
        Label { HStack { Text(t); Spacer(); count(n) } } icon: { Image(systemName: icon) }.font(Theme.font(17, .bold))
    }
    private func count(_ n: Int) -> some View { Text("\(n)").font(Theme.font(15, .bold)).foregroundStyle(Theme.ink3) }

    private var entryList: some View {
        List(selection: $selected) {
            if query.trimmingCharacters(in: .whitespaces).isEmpty, case .stage(let st) = pick ?? .all, st == .review || st == .test {
                Section { StageSummary(stage: st, onReady: { selected = $0 }) }
            }
            if let groups {
                ForEach(groups, id: \.title) { g in
                    Section("\(g.title) · \(g.entries.count)") { ForEach(g.entries) { row($0) } }
                }
            } else {
                ForEach(list) { row($0) }
            }
        }
        .searchable(text: $query, prompt: "搜索,或输入 #12")
        .navigationTitle(title)
        .overlay { if list.isEmpty && !query.isEmpty { ContentUnavailableView.search(text: query) } }
    }

    private func row(_ e: Entry) -> some View {
        HStack(alignment: .top, spacing: 12) {
            RoundedRectangle(cornerRadius: 4).fill(Theme.dot(model.stage(e))).frame(width: 12, height: 12).padding(.top, 6)
            VStack(alignment: .leading, spacing: 4) {
                Text(e.title).font(Theme.font(18, .heavy)).foregroundStyle(Theme.ink).lineLimit(2)
                Text(plain(e.gloss)).font(Theme.font(14, .medium)).foregroundStyle(Theme.ink3).lineLimit(2)
                Text(sub(e)).font(Theme.font(12, .bold)).foregroundStyle(Theme.ink3)
            }
        }
        .padding(.vertical, 6)
        .tag(e.anchor)
    }

    /// #12 · 复习中 · 试 3 · 对 2 · 明天到期
    private func sub(_ e: Entry) -> String {
        let tries = model.attempts(e).filter { !$0.text.isEmpty }
        var xs = [e.no.map { "#\($0)" }, model.stage(e)?.label ?? "文章"].compactMap { $0 }
        if !tries.isEmpty { xs.append("试 \(tries.count) · 对 \(tries.filter(\.ok).count)") }
        if model.stage(e) == .review, let d = model.card(e)?.due {
            let today = dayNo()
            xs.append(d <= today ? "今天到期" : d == today + 1 ? "明天到期" : "\(d - today) 天后到期")
        }
        return xs.joined(separator: " · ")
    }

    private var title: String {
        switch pick ?? .all {
        case .all: return "全部条目"
        case .stage(let s): return s.label
        case .group(let g): return groupLabel(g)
        }
    }

    private func place(of a: String?) -> (at: Int, of: Int)? {
        guard let a, let i = list.firstIndex(where: { $0.anchor == a }) else { return nil }
        return (i + 1, list.count)
    }

    /// 下一个 / 下一条: the next entry of the list it was opened from.
    private func next() {
        guard let a = selected, let i = list.firstIndex(where: { $0.anchor == a }), i + 1 < list.count else { return }
        selected = list[i + 1].anchor
    }
}

struct SettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppModel.self) private var model
    @Bindable private var speaker = Speaker.shared
    @State private var token = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if model.sync.connected {
                        LabeledContent("状态", value: syncLine)
                        Button("立即同步") { Task { await model.syncNow() } }.disabled(model.sync.busy)
                        Button("断开", role: .destructive) { model.disconnect() }
                    } else {
                        SecureField("粘贴 GitHub token", text: $token)
                            .textInputAutocapitalization(.never).autocorrectionDisabled()
                        Button("连接") { model.connect(token); token = "" }
                            .disabled(token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                } header: { Text("同步到 GitHub") } footer: {
                    Text("和 taobao 的网页共用一份进度(progress 分支的 progress-taobao.json):学习阶段、复习排期、自测结果、学习历史。自测里写的句子原文不上传。token 只存在这台 iPad 的钥匙串里,只需要 chinalwb/vocab 的 Contents 读写权限。")
                }
                Section {
                    Picker("句间停顿", selection: $speaker.pause) {
                        ForEach(Speaker.pauses, id: \.self) { Text(String(format: "%.1f 秒", Double($0) / 1000)).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    Button("试听") { speaker.speak("Let's read the essay together, one sentence at a time.") }
                } header: { Text("朗读") } footer: {
                    Text("文章「朗读全文」时两句之间停多久。音色用 iPad 自带的美式英语,在「设置 → 辅助功能 → 朗读内容 → 声音」里可以下载更自然的增强音色。")
                }
            }
            .navigationTitle("设置")
            .formStyle(.grouped)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        }
    }

    private var syncLine: String {
        if model.sync.busy { return "同步中…" }
        if let e = model.sync.error { return "出错:\(e)" }
        guard model.sync.ok > 0 else { return "还没同步过" }
        return "上次同步 " + Date(timeIntervalSince1970: Double(model.sync.ok) / 1000).formatted(date: .abbreviated, time: .shortened)
    }
}

/// A stage's summary on top of its list: the big number, the counts, the start button; and when some
/// entries have every question right, the offer to move them on. Same as summaryHtml() on the page.
struct StageSummary: View {
    let stage: Stage
    let onReady: (String) -> Void
    @Environment(AppModel.self) private var model

    var body: some View {
        let mine = model.inStage(stage), queue = model.quizQueue(stage)
        let ready = mine.filter { model.advanceReady($0) }
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("\(stage == .review ? queue.count : mine.count)").font(Theme.font(52, .black)).foregroundStyle(Theme.ink)
                Text(caption(mine, queue)).font(Theme.font(17, .bold)).foregroundStyle(Theme.ink2)
            }
            if stage == .review {
                let p = model.reviewPlan()
                Text("今天到期 \(p.due.count) · 新加入 \(p.fresh.count)").font(Theme.font(15, .bold)).foregroundStyle(Theme.ink3)
            } else if !mine.isEmpty {
                let by = (0...2).map { g in mine.filter { model.lastGroup($0) == g }.count }
                GeometryReader { g in
                    HStack(spacing: 3) {
                        ForEach(0..<3, id: \.self) { k in
                            if by[k] > 0 {
                                Capsule().fill([Theme.rule, Theme.badInk, Theme.go][k])
                                    .frame(width: max(8, g.size.width * CGFloat(by[k]) / CGFloat(mine.count) - 3))
                            }
                        }
                    }
                }.frame(height: 10)
                Text("没做过 \(by[0]) · 上次没对 \(by[1]) · 上次答对 \(by[2])").font(Theme.font(14, .bold)).foregroundStyle(Theme.ink3)
            }
            Button(queue.isEmpty ? (stage == .review ? "今天没有要复习的" : "还没有考核中的条目")
                   : stage == .review ? "开始复习 \(queue.count) 条 →" : "开始考核 \(queue.count) 题 →") {
                model.run = RunSession(stage: stage, queue: queue.map(\.anchor))
            }
            .buttonStyle(LedgeButtonStyle()).disabled(queue.isEmpty).opacity(queue.isEmpty ? 0.5 : 1)
            if stage == .review {
                Text("先看一遍笔记,再翻面做题(先选词、再打字)。全部答对了下次隔得更久,答错了明天再来;连续答对 \(promoteReps) 次(隔开几天)、例句也都答对过,就自动移到考核。")
                    .font(Theme.font(13, .medium)).foregroundStyle(Theme.ink3)
            }
            if !ready.isEmpty, let nx = stage.next {
                Button { onReady(ready[0].anchor) } label: {
                    Label("\(ready.count) 条的题都答对过了,可以移到\(nx.short)", systemImage: "checkmark.circle.fill")
                        .font(Theme.font(15, .bold)).foregroundStyle(Theme.okInk)
                }.buttonStyle(.borderless)
            }
        }
        .padding(.vertical, 8)
    }

    private func caption(_ mine: [Entry], _ queue: [Entry]) -> String {
        if stage == .test { return "条考核中" }
        return mine.isEmpty ? "条 · 还没有复习中的条目" : queue.isEmpty ? "条 · 今天的都复习完了" : "条 · 今天要复习"
    }
}
