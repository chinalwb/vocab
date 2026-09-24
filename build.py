#!/usr/bin/env python3
"""Generate the 词汇便签墙 page from vocabulary.md.

  python build.py                       -> index.html (standalone, for GitHub Pages)
  python build.py --artifact out.html   -> also write the body-only variant used
                                           when publishing as a Claude artifact
"""
import argparse
import html
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).parent
LEVELS = ["A1", "A2", "B1", "B2", "C1", "C2"]
GROUP_ORDER = LEVELS + ["TERM", "GRAMMAR", "SENTENCE"]


def split_entries(src):
    """Split the markdown on its <a id="..."></a> anchors."""
    parts = re.split(r'\n<a id="([^"]+)"></a>\n', src)
    out = []
    for i in range(1, len(parts), 2):
        anchor, body = parts[i], parts[i + 1]
        if anchor == "toc":
            continue
        m = re.match(r"##\s+(.+)", body.strip())
        if not m:
            continue
        rest = re.sub(r"\n---\s*$", "", body.strip()[m.end():].strip()).strip()
        out.append({"anchor": anchor, "title": m.group(1).strip(), "raw": rest})
    return out


def detect_level(title, raw):
    if title.startswith("句子"):
        return "SENTENCE"
    if title.startswith("语法笔记") or title.startswith("语域笔记"):
        return "GRAMMAR"
    m = re.search(r"CEFR[:：]\s*([^\n]+)", raw)
    if not m:
        return "TERM"
    found = re.findall(r"\b([ABC][12])\b", m.group(1))
    return found[0] if found else "TERM"


def meta_field(raw, label):
    m = re.search(r"^-\s*" + label + r"[:：]\s*(.+)$", raw, re.M)
    return m.group(1).strip() if m else ""


def gloss(raw):
    # a 句子 entry previews best as the original sentence being corrected
    m = re.search(r"^>\s*(.+)$", raw, re.M)
    if m and "**我的原句" in raw:
        return m.group(1).strip()
    for pat in (r"\*\*含义[:：]?\*\*[:：]?\s*(.+)", r"\*\*规则[^*]*\*\*[:：]?\s*(.+)"):
        m = re.search(pat, raw)
        if m:
            return m.group(1).strip()
    for line in raw.split("\n"):
        s = line.strip()
        if s and not s.startswith("-") and not s.startswith("**English definition"):
            return re.sub(r"\*\*(.+?)\*\*", r"\1", s)
    return ""


def inline(t):
    t = html.escape(t)
    t = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", t)
    t = re.sub(r"~~(.+?)~~", r"<del>\1</del>", t)
    t = re.sub(r"`(.+?)`", r"<code>\1</code>", t)
    t = re.sub(r"\[(.+?)\]\(#[^)]+\)", r"<em>\1</em>", t)
    t = re.sub(r"(?<![\w*])\*([^*\n]+)\*(?![\w*])", r"<em>\1</em>", t)
    return t


def to_html(raw):
    # the 音标/词性/CEFR bullets are rendered as chips, not body text
    raw = re.sub(r"^(?:-\s*(?:音标|词性|CEFR)[:：][^\n]*\n?)+", "", raw.strip(), flags=re.M).strip()
    lines = raw.split("\n")
    out, i, ul, ol = [], 0, False, False

    def close():
        nonlocal ul, ol
        if ul:
            out.append("</ul>")
            ul = False
        if ol:
            out.append("</ol>")
            ol = False

    while i < len(lines):
        s = lines[i].strip()
        if not s:
            close()
            i += 1
            continue
        mo = re.match(r"^(\d+)\.\s+(.*)$", s)
        mu = re.match(r"^[-*]\s+(.*)$", s)
        mq = re.match(r"^>\s?(.*)$", s)
        if mq:
            close()
            quoted = [inline(mq.group(1))]
            while i + 1 < len(lines) and re.match(r"^>\s?", lines[i + 1].strip()):
                i += 1
                quoted.append(inline(re.sub(r"^>\s?", "", lines[i].strip())))
            out.append("<blockquote>" + "<br>".join(quoted) + "</blockquote>")
        elif mo:
            if ul:
                out.append("</ul>")
                ul = False
            if not ol:
                out.append('<ol class="ex">')
                ol = True
            en, zh = inline(mo.group(2)), ""
            nxt = lines[i + 1] if i + 1 < len(lines) else ""
            if nxt.startswith("   ") and not re.match(r"^\s*[-*\d]", nxt.strip()):
                zh = inline(nxt.strip())
                i += 1
            out.append(f'<li><span class="en">{en}</span>' + (f'<span class="zh">{zh}</span>' if zh else "") + "</li>")
        elif mu:
            if ol:
                out.append("</ol>")
                ol = False
            if not ul:
                out.append("<ul>")
                ul = True
            out.append(f"<li>{inline(mu.group(1))}</li>")
        else:
            close()
            out.append(f"<p>{inline(s)}</p>")
        i += 1
    close()
    return "".join(out)


def build_data(src):
    grouped = {}
    for e in split_entries(src):
        lvl = detect_level(e["title"], e["raw"])
        grouped.setdefault(lvl, []).append({
            "anchor": e["anchor"],
            "title": e["title"],
            "ipa": meta_field(e["raw"], "音标"),
            "pos": meta_field(e["raw"], "词性"),
            "cefr": meta_field(e["raw"], "CEFR"),
            "gloss": re.sub(r"\*\*(.+?)\*\*", r"\1", gloss(e["raw"]))[:150],
            "html": to_html(e["raw"]),
            "level": lvl,
        })
    return [{"level": l, "items": grouped[l]} for l in GROUP_ORDER if l in grouped]


# Matches the reset the Claude artifact host injects, so both targets render alike.
STANDALONE = """<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<style>
  :root{{color-scheme:light;padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}}
  body{{margin:0;font:14px system-ui,-apple-system,sans-serif}}
  img{{max-width:100%}}
  [hidden]{{display:none!important}}
</style>
{head}
</head>
<body>
{body}
</body>
</html>
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--artifact", metavar="PATH", help="also write the body-only variant here")
    args = ap.parse_args()

    data = build_data((ROOT / "vocabulary.md").read_text(encoding="utf-8"))
    page = (ROOT / "template.html").read_text(encoding="utf-8").replace(
        "/*__DATA__*/", json.dumps(data, ensure_ascii=False))
    if "__DATA__" in page:
        raise SystemExit("template.html is missing its /*__DATA__*/ placeholder")

    head = page.split("<!--HEAD-->", 1)[1].split("<!--/HEAD-->", 1)[0].strip()
    body = page.split("<!--/HEAD-->", 1)[1].strip()
    (ROOT / "index.html").write_text(STANDALONE.format(head=head, body=body), encoding="utf-8")

    if args.artifact:
        pathlib.Path(args.artifact).write_text(
            page.replace("<!--HEAD-->\n", "").replace("<!--/HEAD-->\n", ""), encoding="utf-8")

    total = sum(len(g["items"]) for g in data)
    print(f"{total} entries -> " + ", ".join(f"{g['level']}:{len(g['items'])}" for g in data))


if __name__ == "__main__":
    main()
