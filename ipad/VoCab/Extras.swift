import SwiftUI

/*
 * The two pages after an entry's notes: 记忆 (its estimated forgetting curve) and 我的笔记.
 * Same as memorySlide() / paintMemory() and myNoteSlide() / wireMyNote() on the page, and
 * MemoryCurve.kt / EntryScreen's MyNote on the phone.
 */

/// 记忆: replayed from the entry's 复习 / 考核 days — solid up to today, dashed after (if it isn't
/// reviewed), a dot per graded day on the 100% line (✗ in red), today's dot on the curve. Touch the
/// chart for that day's estimate.
struct MemoryCard: View {
    let entry: Entry
    @Environment(AppModel.self) private var model
    @State private var touched: Int64?

    var body: some View {
        let pts = model.memory(entry)
        VStack(alignment: .leading, spacing: 12) {
            if pts.isEmpty {
                Text("还没在复习或考核里做过这一条。做过一次之后,这里会画出它的遗忘曲线:每复习一次回到顶上,答对了往后掉得更慢。")
                    .foregroundStyle(Theme.ink2)
            } else {
                let today = dayNo()
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text("约 \(Int((recallOn(pts, Double(today)) * 100).rounded()))%").font(Theme.font(34, .black)).foregroundStyle(Theme.ink)
                    Text("现在大概还记得").font(Theme.font(16, .bold)).foregroundStyle(Theme.ink2)
                }
                if let w = when(today) { Text(w).font(Theme.font(15, .bold)).foregroundStyle(Theme.selInk) }
                chart(pts, today: today)
                Text("复习 \(pts.count) 天 · 忘过 \(pts.filter { !$0.ok }.count) 次").font(Theme.font(15, .heavy)).foregroundStyle(Theme.ink2)
                FlowLayout(spacing: 8) {
                    ForEach(pts, id: \.day) { p in
                        Text("\(md(p.day)) \(p.ok ? "✓" : "✗")").font(Theme.font(14, .bold))
                            .foregroundStyle(p.ok ? Theme.ink2 : Theme.badInk)
                            .padding(.horizontal, 8).padding(.vertical, 3)
                            .background(Capsule().fill(Theme.soft))
                    }
                }
                Text("按每次复习 / 考核的对错估算:SM-2 在估计还记得 90% 左右时安排下一次复习,答对一次,下一段就掉得更慢。只是模型推算,不是测出来的;条目页的自测不算。")
                    .font(Theme.font(13, .medium)).foregroundStyle(Theme.ink3)
            }
        }
        .font(Theme.font(17, .medium))
    }

    /// 下次复习 in 复习 (its SM-2 due day); a line for 考核 / 已掌握.
    private func when(_ today: Int64) -> String? {
        switch model.stage(entry) {
        case .review:
            guard let due = model.card(entry)?.due else { return nil }
            return due > today ? "下次复习:\(md(due))(\(due - today) 天后)" : due == today ? "今天到期" : "已经过了 \(today - due) 天"
        case .test: return "考核中,不按日子排"
        case .done: return "已掌握,不再排期"
        default: return nil
        }
    }

    private func md(_ day: Int64) -> String {
        var utc = Calendar(identifier: .gregorian); utc.timeZone = TimeZone(identifier: "UTC")!
        let c = utc.dateComponents([.month, .day], from: Date(timeIntervalSince1970: Double(day) * 86400))
        return "\(c.month!)/\(c.day!)"
    }

    private func chart(_ pts: [MemoryPoint], today: Int64) -> some View {
        let due = model.stage(entry) == .review ? model.card(entry)?.due : nil
        let last = pts.last!
        let horizon = max(today, last.day + Int64(last.interval), due ?? 0)
        let x0 = Double(pts[0].day)
        let x1 = max(x0 + 4, Double(horizon) + max(1, ((Double(horizon) - x0) * 0.15).rounded(.up)))
        let xs = stride(from: x0, through: x1, by: 0.25).map { $0 }
        // the moment just before each later graded day: the bottom of the tooth
        let pre = pts.dropFirst().map { (d: Double($0.day), r: recallOn(pts, Double($0.day) - 1e-6)) }
        let lo = min(0.5, ((xs.map { recallOn(pts, $0) } + pre.map(\.r)).min()! * 10).rounded(.down) / 10)
        let H: CGFloat = 200, L: CGFloat = 44, R: CGFloat = 14, T: CGFloat = 12, B: CGFloat = 28

        return GeometryReader { g in
            let W = g.size.width
            let X = { (d: Double) in L + CGFloat((d - x0) / (x1 - x0)) * (W - L - R) }
            let Y = { (r: Double) in T + CGFloat((1 - r) / (1 - lo)) * (H - T - B) }
            let path = { (from: Double, to: Double) -> Path in
                var p = Path()
                for d in xs.filter({ $0 >= from && $0 <= to }) + [to] {
                    if let j = pre.first(where: { $0.d > from && abs($0.d - d) < 1e-9 }) {
                        p.addLine(to: CGPoint(x: X(d), y: Y(j.r)))
                    }
                    let pt = CGPoint(x: X(d), y: Y(recallOn(pts, d)))
                    if p.isEmpty { p.move(to: pt) } else { p.addLine(to: pt) }
                }
                return p
            }
            let now = min(Double(today), x1)
            ZStack(alignment: .topLeading) {
                Canvas { ctx, _ in
                    for (r, t) in [(1.0, "100%"), (recallAtDue, "90%"), (lo, "\(Int((lo * 100).rounded()))%")] {
                        ctx.stroke(Path { $0.move(to: CGPoint(x: L, y: Y(r))); $0.addLine(to: CGPoint(x: W - R, y: Y(r))) }, with: .color(Theme.rule), lineWidth: 1)
                        ctx.draw(Text(t).font(Theme.font(12, .bold)).foregroundStyle(Theme.ink3), at: CGPoint(x: L - 6, y: Y(r)), anchor: .trailing)
                    }
                    var area = path(x0, now)
                    area.addLine(to: CGPoint(x: X(now), y: Y(lo))); area.addLine(to: CGPoint(x: X(x0), y: Y(lo))); area.closeSubpath()
                    ctx.fill(area, with: .color(Theme.selBg))
                    ctx.stroke(path(x0, now), with: .color(Theme.selInk), style: StrokeStyle(lineWidth: 2.5, lineJoin: .round))
                    if Double(today) < x1 {
                        ctx.stroke(path(Double(today), x1), with: .color(Theme.selInk), style: StrokeStyle(lineWidth: 2, dash: [5, 4]))
                    }
                    ctx.stroke(Path { $0.move(to: CGPoint(x: X(Double(today)), y: T)); $0.addLine(to: CGPoint(x: X(Double(today)), y: H - B)) },
                               with: .color(Theme.ink3), style: StrokeStyle(lineWidth: 1, dash: [2, 3]))
                    for p in pts {
                        let c = CGRect(x: X(Double(p.day)) - 5, y: Y(1) - 5, width: 10, height: 10)
                        ctx.fill(Path(ellipseIn: c.insetBy(dx: -2, dy: -2)), with: .color(Theme.ground))
                        ctx.fill(Path(ellipseIn: c), with: .color(p.ok ? Theme.ink : Theme.badInk))
                    }
                    let n = CGRect(x: X(Double(today)) - 6, y: Y(recallOn(pts, Double(today))) - 6, width: 12, height: 12)
                    ctx.fill(Path(ellipseIn: n), with: .color(Theme.selInk))
                    // x labels: today, the next due day, the first day — the ones that crowd are left out
                    var placed: [CGFloat] = []
                    for (d, t) in [(today, "今天")] + (due.map { $0 > today ? [($0, md($0))] : [] } ?? []) + [(pts[0].day, md(pts[0].day))] {
                        let x = X(Double(d))
                        guard placed.allSatisfy({ abs($0 - x) > 44 }) else { continue }
                        placed.append(x)
                        ctx.draw(Text(t).font(Theme.font(12, .bold)).foregroundStyle(Theme.ink3), at: CGPoint(x: x, y: H - 10))
                    }
                    if let d = touched {
                        ctx.stroke(Path { $0.move(to: CGPoint(x: X(Double(d)), y: T)); $0.addLine(to: CGPoint(x: X(Double(d)), y: H - B)) },
                                   with: .color(Theme.ink2), lineWidth: 1.5)
                    }
                }
                if let d = touched {
                    let p = pts.first { $0.day == d }
                    (Text("约 \(Int((recallOn(pts, Double(d)) * 100).rounded()))%").bold()
                     + Text(" · \(d == today ? "今天" : md(d))" + (p.map { $0.ok ? " · 复习答对" : " · 复习没对" } ?? (d > today ? " · 不复习的话" : ""))))
                        .font(Theme.font(13, .semibold)).foregroundStyle(Theme.ground)
                        .padding(.horizontal, 10).padding(.vertical, 5)
                        .background(Capsule().fill(Theme.ink))
                        .fixedSize()
                        .position(x: min(max(X(Double(d)), 90), W - 90), y: T + 4)
                }
            }
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0).onChanged { v in
                let d = x0 + Double((v.location.x - L) / (W - L - R)) * (x1 - x0)
                touched = Int64(min(max(d, x0), x1).rounded())
            })
        }
        .frame(height: H)
    }
}

/// 我的笔记: a box that saves as I type (half a second after the last key), and when I leave it.
struct MyNote: View {
    let entry: Entry
    @Environment(AppModel.self) private var model
    @State private var text = ""
    @State private var loaded = false
    @State private var pending: Task<Void, Never>?
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            TextField("写下自己的理解、例句、容易混的地方…", text: $text, axis: .vertical)
                .font(Theme.font(19, .medium)).lineLimit(6...30)
                .focused($focused)
                .padding(14)
                .background(RoundedRectangle(cornerRadius: 14).fill(Theme.ground))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(focused ? Theme.selEdge : Theme.rule, lineWidth: 2))
            Text("自动保存。开了同步的话会存进 GitHub 的公开仓库,别写隐私内容。").font(Theme.font(13, .medium)).foregroundStyle(Theme.ink3)
        }
        .onAppear { if !loaded { text = model.noteOf(entry); loaded = true } }
        .onChange(of: text) {
            pending?.cancel()
            pending = Task { try? await Task.sleep(for: .milliseconds(500)); if !Task.isCancelled { model.setNote(entry, text) } }
        }
        .onChange(of: focused) { if !focused { pending?.cancel(); model.setNote(entry, text) } }
        .onDisappear { pending?.cancel(); if loaded { model.setNote(entry, text) } }
    }
}
