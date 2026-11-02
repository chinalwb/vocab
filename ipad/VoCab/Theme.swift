import SwiftUI

/// taobao's look, after duo.css (B · 活泼友好): white ground, heavy rounded type, light-grey rims with a
/// solid "ledge" under cards and buttons, green for the main action, blue for what's selected.
enum Theme {
    static func dyn(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light) })
    }
    static let ground = dyn(0xFFFFFF, 0x131F24)
    static let ink = dyn(0x3C3C3C, 0xF1F7FB)
    static let ink2 = dyn(0x5F5F5F, 0xC3D0D7)
    static let ink3 = dyn(0x8A8A8A, 0x9EB0BA)
    static let rule = dyn(0xE5E5E5, 0x37464F)
    static let soft = dyn(0xF7F7F7, 0x202F36)
    static let tag = dyn(0xF0F0F0, 0x202F36)
    static let go = dyn(0x58CC02, 0x58CC02)
    static let goEdge = dyn(0x46A302, 0x58A700)
    static let blue = dyn(0x1CB0F6, 0x1CB0F6)
    static let blueEdge = dyn(0x1899D6, 0x1899D6)
    static let selBg = dyn(0xDDF4FF, 0x1B3540)
    static let selEdge = dyn(0x84D8FF, 0x3F85A7)
    static let selInk = dyn(0x0A78AE, 0x49C0F8)
    static let okBg = dyn(0xD7FFB8, 0x1E3A12)
    static let okInk = dyn(0x3D7A00, 0x9BE15D)
    static let badBg = dyn(0xFFDFE0, 0x3F1C1F)
    static let badInk = dyn(0xC41D1D, 0xFF8A8A)
    static let missBg = dyn(0xFFF0DB, 0x43300F)
    static let missInk = dyn(0xA14D00, 0xFFC46B)
    static let brand = dyn(0x46A302, 0x79D634)
    static let mistakeWash = Color(red: 0.9, green: 0.28, blue: 0.3).opacity(0.25)

    /// cardColor "stage": a card is its stage's pale wash, its dot the stage colour.
    static func dot(_ s: Stage?) -> Color {
        switch s {
        case .learn: return dyn(0xAFAFAF, 0x6F8590)
        case .review: return dyn(0x1CB0F6, 0x49C0F8)
        case .test: return dyn(0xFF9600, 0xFFAB33)
        case .done: return dyn(0x58CC02, 0x79D634)
        case nil: return dyn(0x8B5CF6, 0xB49CFB) // 文章
        }
    }
    static func wash(_ s: Stage?) -> Color {
        switch s {
        case .learn: return dyn(0xF1F1F1, 0x24323A)
        case .review: return dyn(0xE3F5FE, 0x1B3540)
        case .test: return dyn(0xFFEDD6, 0x3A3121)
        case .done: return dyn(0xE7F8D9, 0x22381A)
        case nil: return dyn(0xEFE4FA, 0x3B284F)
        }
    }

    static func font(_ size: CGFloat, _ weight: Font.Weight = .semibold) -> Font {
        .system(size: size, weight: weight, design: .rounded)
    }
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(red: CGFloat(hex >> 16 & 0xFF) / 255, green: CGFloat(hex >> 8 & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
    }
}

/// A Duolingo-style button: a solid face over a darker 4pt ledge that it presses down into.
struct LedgeButtonStyle: ButtonStyle {
    var face: Color = Theme.go
    var edge: Color = Theme.goEdge
    var ink: Color = .white
    var outline = false

    func makeBody(configuration c: Configuration) -> some View {
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        let down = c.isPressed
        return c.label
            .font(Theme.font(17, .heavy))
            .foregroundStyle(outline ? Theme.ink2 : ink)
            .padding(.horizontal, 20).padding(.vertical, 12)
            .frame(minHeight: 48)
            .background(shape.fill(outline ? Theme.ground : face))
            .overlay(shape.stroke(outline ? Theme.rule : .clear, lineWidth: 2))
            .background(shape.fill(outline ? Theme.rule : edge).offset(y: down ? 0 : 4))
            .offset(y: down ? 4 : 0)
            .padding(.bottom, 4)
    }
}

/// A light card with a 2pt rim and the ledge under it.
struct LedgeCard: ViewModifier {
    var fill: Color = Theme.ground
    var radius: CGFloat = 18
    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        return content
            .background(shape.fill(fill))
            .overlay(shape.stroke(Theme.rule, lineWidth: 2))
            .background(shape.fill(Theme.rule).offset(y: 4))
    }
}

extension View {
    func ledgeCard(_ fill: Color = Theme.ground, radius: CGFloat = 18) -> some View { modifier(LedgeCard(fill: fill, radius: radius)) }
}

/// Lays its children out in rows, wrapping like words: chips, filled-in blanks.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    var lineSpacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        rows(proposal.width ?? .infinity, subviews).size
    }
    func placeSubviews(in b: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for (i, p) in rows(b.width, subviews).points.enumerated() {
            subviews[i].place(at: CGPoint(x: b.minX + p.x, y: b.minY + p.y), proposal: .unspecified)
        }
    }
    private func rows(_ width: CGFloat, _ subviews: Subviews) -> (points: [CGPoint], size: CGSize) {
        var pts: [CGPoint] = [], x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, maxX: CGFloat = 0
        var rowStart = 0
        var heights: [CGFloat] = []
        for (i, v) in subviews.enumerated() {
            let s = v.sizeThatFits(.unspecified)
            if x > 0 && x + s.width > width {
                // centre the row's items on its line
                for j in rowStart..<i { pts[j].y += (rowH - heights[j]) / 2 }
                x = 0; y += rowH + lineSpacing; rowH = 0; rowStart = i
            }
            pts.append(CGPoint(x: x, y: y)); heights.append(s.height)
            x += s.width + spacing; rowH = max(rowH, s.height); maxX = max(maxX, x - spacing)
        }
        for j in rowStart..<pts.count { pts[j].y += (rowH - heights[j]) / 2 }
        return (pts, CGSize(width: maxX, height: y + rowH))
    }
}
