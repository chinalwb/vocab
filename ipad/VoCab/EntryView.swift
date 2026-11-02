import SwiftUI

/// The entry page: title and 🔊, the four-stage track, then the card — notes on the front, 自测 on the
/// back (V2 layout, like the web's docked entry). 考核 opens straight on the 自测 side.
struct EntryView: View {
    let entry: Entry
    /// Where it sits in the list it was opened from, for 「3 / 12 · 下一个 ›」.
    var place: (at: Int, of: Int)?
    var onNext: (() -> Void)?
    var onXref: (String) -> Void

    @Environment(AppModel.self) private var model
    @State private var quizzing = false
    @State private var full = false
    @State private var page = 0
    /// the notes pages a new entry still has to show before 自测这条 (nil once they're all seen)
    @State private var unseen: Set<Int>?
    @State private var flash: String?
    @AppStorage("notesAll") private var notesAll = false
    private let speaker = Speaker.shared

    private var stage: Stage? { model.stage(entry) }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            if !full {
                header
                if let stage { StageTrack(stage: stage) { model.move(entry, to: $0) } }
            }
            card
        }
        .padding(.horizontal, 24).padding(.top, 8).padding(.bottom, 16)
        .background(Theme.ground)
        .toolbar {
            if full { ToolbarItem(placement: .principal) { Text(entry.title).font(Theme.font(17, .heavy)).lineLimit(1) } }
            if let place, let onNext, place.at < place.of {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onNext) { Text("\(place.at) / \(place.of) · 下一个 ›").font(Theme.font(15, .bold)) }
                }
            }
        }
        .onAppear {
            quizzing = stage == .test || UserDefaults.standard.bool(forKey: "quiz")
            model.visit(entry, via: "entry")
            page = UserDefaults.standard.integer(forKey: "page")   // `-page N` (simulator testing)
            // a new entry shows 自测这条 once every page of its notes has been seen (用户 2026-11-01);
            // 记忆 / 我的笔记 don't count
            if model.needsReading(entry) { unseen = Set(1..<entry.sections.count); if notesAll { saw(all: true) } }
        }
        .onChange(of: page) { saw(page) }
        .onChange(of: notesAll) { if notesAll { saw(all: true) } }
        .overlay(alignment: .bottom) {
            if let flash {
                Text(flash).font(Theme.font(16, .bold)).foregroundStyle(Theme.ground)
                    .padding(.horizontal, 18).padding(.vertical, 12).background(Capsule().fill(Theme.ink))
                    .padding(.bottom, 90).transition(.opacity)
            }
        }
        .onChange(of: entry.anchor) { quizzing = model.stage(entry) == .test; speaker.stop() }
        .onDisappear { speaker.stop() }
        .environment(\.openURL, OpenURLAction { url in
            if url.scheme == xrefScheme { onXref(url.absoluteString.replacingOccurrences(of: "\(xrefScheme):", with: "")); return .handled }
            return .systemAction
        })
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text(entry.title).font(Theme.font(32, .heavy)).foregroundStyle(Theme.ink)
                    .fixedSize(horizontal: false, vertical: true)
                if entry.isEssay {
                    // 朗读全文: sentence by sentence, the pause from 设置 between two
                    Button { speaker.readAll(entry) } label: {
                        Image(systemName: speaker.readingAnchor == entry.anchor ? "stop.fill" : "text.line.first.and.arrowtriangle.forward")
                            .font(.system(size: 22, weight: .bold)).foregroundStyle(Theme.selInk)
                    }.buttonStyle(.plain).accessibilityLabel(speaker.readingAnchor == entry.anchor ? "停止朗读" : "朗读全文")
                } else if !entry.speakText.isEmpty {
                    SpeakButton(text: entry.speakText, size: 22)
                }
            }
            Text(meta).font(Theme.font(14, .bold)).foregroundStyle(Theme.ink3)
                .padding(.horizontal, 12).padding(.vertical, 6).background(Capsule().fill(Theme.soft))
        }
    }

    private var meta: String {
        ([groupLabel(entry.level)] + [entry.no.map { "#\($0)" }, entry.pos, entry.ipa, entry.date].compactMap { $0 }.filter { !$0.isEmpty })
            .joined(separator: " · ")
    }

    private var card: some View {
        ZStack {
            NotesCard(entry: entry, reading: readingSentence, extras: extras, unseen: unseen ?? [], page: $page) {
                Button { withAnimation(.snappy(duration: 0.3)) { full.toggle() } } label: {
                    Image(systemName: full ? "arrow.down.right.and.arrow.up.left" : "arrow.up.left.and.arrow.down.right")
                        .font(.system(size: 16, weight: .bold)).frame(width: 34, height: 34)
                }.buttonStyle(.plain).foregroundStyle(Theme.ink2).accessibilityLabel(full ? "退出全屏" : "全屏")
            } footer: {
                foot
            }
            .opacity(quizzing ? 0 : 1)
            .rotation3DEffect(.degrees(quizzing ? -90 : 0), axis: (0, 1, 0))

            if quizzing {
                QuizView(entry: entry,
                         onNotes: stage == .test ? nil : { flip(false) },
                         onNextEntry: onNext)
                    .transition(.asymmetric(insertion: .opacity.combined(with: .scale(scale: 0.97)), removal: .opacity))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .ledgeCard(quizzing ? Theme.ground : Theme.wash(stage), radius: 22)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var readingSentence: String? {
        guard speaker.readingAnchor == entry.anchor, let k = speaker.reading, k < entry.quizExamples.count else { return nil }
        return plain(entry.quizExamples[k].en)
    }

    /// 记忆 once the entry has a graded day or is past 学习中; 我的笔记 always.
    private var extras: [NotesExtra] {
        let memory = !model.memory(entry).isEmpty || (stage != nil && stage != .learn)
        return (memory ? [.memory] : []) + [.note]
    }

    private func saw(_ k: Int = 0, all: Bool = false) {
        guard var u = unseen else { return }
        if all { u = [] } else { u.remove(k) }
        if u.isEmpty { unseen = nil; model.markSeen(entry) } else { unseen = u }
    }

    private func say(_ t: String) {
        withAnimation { flash = t }
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) { withAnimation { if flash == t { flash = nil } } }
    }

    private func flip(_ on: Bool) { withAnimation(.easeInOut(duration: 0.35)) { quizzing = on } }

    private var foot: some View {
        HStack(spacing: 12) {
            Text(unseen.map { "看完每一页笔记就能自测(还有 \($0.count) 页没看,标了圆点)" } ?? hint)
                .font(Theme.font(15, .semibold)).foregroundStyle(Theme.ink2)
            Spacer()
            if let u = unseen {
                if let ahead = u.filter({ $0 > page }).min() {
                    Button("看下页 ›") { withAnimation(.snappy) { page = ahead } }.buttonStyle(LedgeButtonStyle())
                } else {
                    // greyed out, not disabled: a tap says why
                    Button("自测这条 →") { say("还有 \(u.count) 页笔记没看(标了圆点),看完就能自测") }
                        .buttonStyle(LedgeButtonStyle(face: Theme.rule, edge: Theme.rule, ink: Theme.ink3))
                }
            } else {
                footButtons
            }
        }
        .padding(.horizontal, 16).padding(.top, 10).padding(.bottom, 8)
    }

    @ViewBuilder private var footButtons: some View {
            if let s = stage, s != .learn, model.advanceReady(entry), let nx = s.next {
                // 移到 X = the move and the next entry; the track above only moves
                Button("移到\(nx.short)") { model.move(entry, to: nx); onNext?() }.buttonStyle(LedgeButtonStyle(outline: true))
            }
            if stage == .review && model.reviewDue(entry) {
                // 复习这一条: a run of one (its notes just read), which grades SM-2 — the page's 自测 never does
                Button("复习这一条 →") { speaker.stop(); model.run = RunSession(stage: .review, queue: [entry.anchor], studied: true) }
                    .buttonStyle(LedgeButtonStyle())
            } else {
                Button(entry.isEssay ? "选词练习 →" : "自测这条 →") { speaker.stop(); flip(true) }.buttonStyle(LedgeButtonStyle())
            }
    }

    private var hint: String {
        if entry.isEssay { return "共 \(entry.quizExamples.count) 句 · 只选词,选对就算这句答对" }
        guard let s = stage else { return "" }
        guard let next = s.next else { return "已掌握,不再进复习和考核" }
        let qs = model.questions(entry)
        let left = remaining(model.attempts(entry), since: model.since(entry), questions: qs)
        if s == .review {
            // and the SM-2 streak; then a run moves it
            let more = promoteReps - model.reps(entry)
            let todo = [(left ?? 0) > 0 ? (entry.level == "SELFTEST" ? "答对这道题" : "再答对 \(left!) 句") : nil,
                        more > 0 ? "复习里再连续答对 \(more) 次" : nil].compactMap { $0 }
            return todo.isEmpty ? "都做到了,可以移到\(next.short)" : todo.joined(separator: ",") + ",就自动移到\(next.short)"
        }
        guard let r = left else { return "这条没有例句,自己判断写没写对" }
        if r > 0 { return entry.level == "SELFTEST" ? "答对这道题可移到\(next.short)" : "再答对 \(r) 句可移到\(next.short)" }
        return "题都答对过了,可以移到\(next.short)"
    }
}

/// 学习中 → 复习中 → 考核中 → 已掌握 as a track: tap a node to move there (it only moves).
struct StageTrack: View {
    let stage: Stage
    let onMove: (Stage) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Stage.allCases) { s in
                let reached = s.index <= stage.index
                Button { if s != stage { onMove(s) } } label: {
                    HStack(spacing: 8) {
                        ZStack {
                            Circle().fill(reached ? Theme.dot(s) : Theme.ground).frame(width: 22, height: 22)
                            Circle().stroke(reached ? Theme.dot(s) : Theme.rule, lineWidth: 2.5).frame(width: 22, height: 22)
                            if s.index < stage.index { Image(systemName: "checkmark").font(.system(size: 11, weight: .black)).foregroundStyle(.white) }
                            if s == stage { Circle().fill(.white).frame(width: 8, height: 8) }
                        }
                        Text(s.label).font(Theme.font(15, s == stage ? .heavy : .semibold))
                            .foregroundStyle(s == stage ? Theme.ink : Theme.ink3)
                    }
                    .padding(.vertical, 6).contentShape(Rectangle())
                }.buttonStyle(.plain)
                if s != .done {
                    Rectangle().fill(s.index < stage.index ? Theme.dot(stage) : Theme.rule).frame(height: 3)
                        .frame(maxWidth: .infinity).padding(.horizontal, 10)
                }
            }
        }
        .animation(.snappy, value: stage)
        .accessibilityElement(children: .contain)
    }
}
