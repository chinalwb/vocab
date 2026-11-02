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

    private var list: [Entry] {
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
            // `-open <anchor>` at launch (simulator testing) opens that entry; `-quiz YES` flips to 自测
            if let a = UserDefaults.standard.string(forKey: "open") { selected = a; columns = .detailOnly }
            await model.refresh()
        }
        .onChange(of: phase) { if phase == .active { Task { await model.refresh() } } }
        .onChange(of: selected) { trail = [] }
        .sheet(isPresented: $settings) { SettingsView() }
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
        List(list, selection: $selected) { e in
            HStack(alignment: .top, spacing: 12) {
                RoundedRectangle(cornerRadius: 4).fill(Theme.dot(model.stage(e))).frame(width: 12, height: 12).padding(.top, 6)
                VStack(alignment: .leading, spacing: 4) {
                    Text(e.title).font(Theme.font(18, .heavy)).foregroundStyle(Theme.ink).lineLimit(2)
                    Text(plain(e.gloss)).font(Theme.font(14, .medium)).foregroundStyle(Theme.ink3).lineLimit(2)
                    Text(([e.no.map { "#\($0)" }, model.stage(e)?.label ?? "文章"].compactMap { $0 }).joined(separator: " · "))
                        .font(Theme.font(12, .bold)).foregroundStyle(Theme.ink3)
                }
            }
            .padding(.vertical, 6)
            .tag(e.anchor)
        }
        .searchable(text: $query, prompt: "搜索,或输入 #12")
        .navigationTitle(title)
        .overlay { if list.isEmpty { ContentUnavailableView.search(text: query) } }
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
    @Bindable private var speaker = Speaker.shared

    var body: some View {
        NavigationStack {
            Form {
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
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        }
    }
}
