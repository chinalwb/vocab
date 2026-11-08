"""The iPad app's icon (用户 2026-11-02): a white word card with a green "Aa" and a blue ✓ badge on
Duolingo green, in the raised-ledge style of taobao's duo.css. Drawn at 4× and scaled down.

    python3 ipad/tools/make_icon.py   # writes VoCab/Assets.xcassets/AppIcon.appiconset/icon.png
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

S = 4                                   # supersampling
N = 1024 * S
GREEN_TOP, GREEN_BOTTOM = (0x6B, 0xD8, 0x0A), (0x46, 0xA3, 0x02)
LEDGE = (0x3A, 0x86, 0x02)
INK = (0x58, 0xCC, 0x02)
BLUE, BLUE_EDGE = (0x1C, 0xB0, 0xF6), (0x18, 0x99, 0xD6)
OUT = Path(__file__).resolve().parent.parent / "VoCab/Assets.xcassets/AppIcon.appiconset/icon.png"


def px(v):
    return int(v * S)


def font(size):
    f = ImageFont.truetype("/System/Library/Fonts/SFCompactRounded.ttf", px(size))
    try:
        f.set_variation_by_name("Black")
    except Exception:
        pass
    return f


# the ground: a soft top-to-bottom green
img = Image.new("RGB", (N, N))
d = ImageDraw.Draw(img)
for y in range(N):
    t = y / (N - 1)
    d.line([(0, y), (N, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(GREEN_TOP, GREEN_BOTTOM)))

# the card on its own layer, tilted a little: its ledge, then the face, then "Aa"
card = Image.new("RGBA", (N, N), (0, 0, 0, 0))
c = ImageDraw.Draw(card)
x0, y0, x1, y1 = px(222), px(222), px(802), px(770)
c.rounded_rectangle([x0, y0 + px(34), x1, y1 + px(34)], radius=px(112), fill=LEDGE)
c.rounded_rectangle([x0, y0, x1, y1], radius=px(112), fill="white")
f = font(330)
box = c.textbbox((0, 0), "Aa", font=f)
w, h = box[2] - box[0], box[3] - box[1]
c.text(((x0 + x1 - w) / 2 - box[0], (y0 + y1 - h) / 2 - box[1] - px(6)), "Aa", font=f, fill=INK)
card = card.rotate(-7, resample=Image.BICUBIC, center=(N / 2, N / 2))
img.paste(card, (0, 0), card)

# the ✓ badge at the card's lower right, with its own ledge
d = ImageDraw.Draw(img)
cx, cy, r = px(770), px(764), px(132)
d.ellipse([cx - r, cy - r + px(22), cx + r, cy + r + px(22)], fill=BLUE_EDGE)
d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=BLUE)
d.ellipse([cx - r, cy - r, cx + r, cy + r], outline="white", width=px(16))
pts = [(cx - px(58), cy + px(4)), (cx - px(14), cy + px(48)), (cx + px(62), cy - px(46))]
d.line(pts, fill="white", width=px(34), joint="curve")
for p in (pts[0], pts[-1]):
    d.ellipse([p[0] - px(17), p[1] - px(17), p[0] + px(17), p[1] + px(17)], fill="white")

OUT.parent.mkdir(parents=True, exist_ok=True)
img.resize((1024, 1024), Image.LANCZOS).save(OUT)
print(OUT)
