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
        .onAppear { quizzing = stage == .test || UserDefaults.standard.bool(forKey: "quiz") }
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
            NotesCard(entry: entry, reading: readingSentence) {
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

    private func flip(_ on: Bool) { withAnimation(.easeInOut(duration: 0.35)) { quizzing = on } }

    private var foot: some View {
        HStack(spacing: 12) {
            Text(hint).font(Theme.font(15, .semibold)).foregroundStyle(Theme.ink2)
            Spacer()
            Button(entry.isEssay ? "选词练习 →" : "自测这条 →") { speaker.stop(); flip(true) }.buttonStyle(LedgeButtonStyle())
        }
        .padding(.horizontal, 16).padding(.top, 10).padding(.bottom, 8)
    }

    private var hint: String {
        if entry.isEssay { return "共 \(entry.quizExamples.count) 句 · 只选词,选对就算这句答对" }
        guard let s = stage else { return "" }
        guard let next = s.next else { return "已掌握" }
        guard let r = remaining(model.attempts(entry), since: model.since(entry), questions: entry.questionKeys) else {
            return "这条没有例句,自己判断写没写对"
        }
        if r > 0 { return "再答对 \(r) 句可移到\(next.short)" }
        return s == .review ? "这一阶段的题都答对过了,可以在上面移到考核" : "这一阶段的题都答对过了"
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
