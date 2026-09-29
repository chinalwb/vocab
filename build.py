#!/usr/bin/env python3
"""Generate the 词汇便签墙 page from vocabulary.md.

  python build.py                       -> index.html (standalone, for GitHub Pages)
  python build.py --artifact out.html   -> also write the body-only variant used
                                           when publishing as a Claude artifact
Every run also writes data.json + meta.json, which the Android app (android/)
fetches from Pages.
"""
import argparse
import datetime
import hashlib
import html
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).parent
LEVELS = ["A1", "A2", "B1", "B2", "C1", "C2"]
GROUP_ORDER = LEVELS + ["TERM", "GRAMMAR", "SENTENCE"]
SCHEMA = 1  # bump when data.json changes shape in a way the app must know about

# 错题本: a bullet like `- ❌ [介词] **...** —— ...` (or ⚠️) marks a mistake the
# user made. Only the user's own mistakes get a tag; demo counter-examples stay
# untagged. The order here is the order the mistakes view lists them in.
# android/.../ui/Inline.kt renders the same tags — keep both lists in sync.
MISTAKE_TYPES = [
    ("冠词", "冠词、可数与单复数"),
    ("介词", "介词搭配"),
    ("搭配", "固定搭配与用词"),
    ("动词", "时态、语态、主谓一致、非谓语"),
    ("结构", "句型、从句与语序"),
    ("词形", "拼写、连字符、大小写、比较级"),
    ("标点", "逗号粘连等标点问题"),
    ("逻辑", "指代、比较对象、歧义、悬垂修饰"),
    ("直译", "中式直译与冗余"),
    ("语气", "语域、正式度与语气轻重"),
]
TYPE_KEYS = [k for k, _ in MISTAKE_TYPES]
MISTAKE_RE = re.compile(r"^(❌|⚠️)\s*\[([^\]]+)\](?!\()\s*(.+)$")


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


def gloss(raw, level):
    # a 句子 entry previews best as the original sentence being corrected
    m = re.search(r"^>\s*(.+)$", raw, re.M)
    if m and level == "SENTENCE" and "**我的原句" in raw:
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
    # 错题本 tags first, so a tag is never swallowed into a later [text](#anchor)
    t = re.sub(r"\[(" + "|".join(TYPE_KEYS) + r")\](?!\()", r'<span class="mtag">\1</span>', t)
    # cross-references become real links the page turns into card-to-card jumps
    t = re.sub(r"\[(.+?)\]\(#([^)]+)\)", r'<a class="xref" href="#\2" data-ref="\2">\1</a>', t)
    t = re.sub(r"(?<![\w*])\*([^*\n]+)\*(?![\w*])", r"<em>\1</em>", t)
    return t


def strip_meta(raw):
    # the 音标/词性/CEFR/日期 bullets are rendered as chips, not body text
    return re.sub(r"^(?:-\s*(?:音标|词性|CEFR|日期)[:：][^\n]*\n?)+", "", raw.strip(), flags=re.M).strip()


def parse_blocks(raw):
    """Turn an entry body into blocks whose text keeps its inline markdown.

    This is the one parser both outputs share: index.html renders the blocks
    to HTML, data.json ships them as-is for the Android app to render natively.
      {"t": "p", "text": s}
      {"t": "ul", "items": [s, ...]}
      {"t": "ol", "items": [{"en": s, "zh": s}, ...]}   (zh may be "")
      {"t": "quote", "lines": [s, ...]}
    """
    lines = strip_meta(raw).split("\n")
    out, cur, i = [], None, 0
    while i < len(lines):
        s = lines[i].strip()
        if not s:
            cur = None
            i += 1
            continue
        mo = re.match(r"^(\d+)\.\s+(.*)$", s)
        mu = re.match(r"^[-*]\s+(.*)$", s)
        mq = re.match(r"^>\s?(.*)$", s)
        if mq:
            quoted = [mq.group(1)]
            while i + 1 < len(lines) and re.match(r"^>\s?", lines[i + 1].strip()):
                i += 1
                quoted.append(re.sub(r"^>\s?", "", lines[i].strip()))
            out.append({"t": "quote", "lines": quoted})
            cur = None
        elif mo:
            if not cur or cur["t"] != "ol":
                cur = {"t": "ol", "items": []}
                out.append(cur)
            en, zh = mo.group(2), ""
            nxt = lines[i + 1] if i + 1 < len(lines) else ""
            if nxt.startswith("   ") and not re.match(r"^\s*[-*\d]", nxt.strip()):
                zh = nxt.strip()
                i += 1
            cur["items"].append({"en": en, "zh": zh})
        elif mu:
            if not cur or cur["t"] != "ul":
                cur = {"t": "ul", "items": []}
                out.append(cur)
            cur["items"].append(mu.group(1))
        else:
            out.append({"t": "p", "text": s})
            cur = None
        i += 1
    return out


def to_html(blocks):
    out = []
    for b in blocks:
        if b["t"] == "p":
            out.append(f"<p>{inline(b['text'])}</p>")
        elif b["t"] == "ul":
            out.append("<ul>" + "".join(f"<li>{inline(x)}</li>" for x in b["items"]) + "</ul>")
        elif b["t"] == "ol":
            out.append('<ol class="ex">' + "".join(
                f'<li><span class="en">{inline(x["en"])}</span>'
                + (f'<span class="zh">{inline(x["zh"])}</span>' if x["zh"] else "") + "</li>"
                for x in b["items"]) + "</ol>")
        else:
            out.append("<blockquote>" + "<br>".join(inline(x) for x in b["lines"]) + "</blockquote>")
    return "".join(out)


def extract_mistakes(anchor, blocks):
    """Collect the tagged ❌/⚠️ bullets, each with the 我的原句 quote it follows."""
    out, original, want_quote = [], "", False
    for b in blocks:
        if b["t"] == "p" and b["text"].startswith("**我的原句"):
            want_quote = True
        elif b["t"] == "quote" and want_quote:
            original, want_quote = "\n".join(b["lines"]), False
        elif b["t"] == "ul":
            for item in b["items"]:
                m = MISTAKE_RE.match(item)
                if not m:
                    continue
                if m.group(2) not in TYPE_KEYS:
                    raise SystemExit(f"{anchor}: unknown mistake type [{m.group(2)}] — use one of {TYPE_KEYS}")
                out.append({"sev": "error" if m.group(1) == "❌" else "warn",
                            "type": m.group(2), "text": m.group(3), "original": original})
    return out


def build_entries(src):
    out = []
    for e in split_entries(src):
        level = detect_level(e["title"], e["raw"])
        blocks = parse_blocks(e["raw"])
        entry = {
            "anchor": e["anchor"],
            "title": e["title"],
            "level": level,
            "ipa": meta_field(e["raw"], "音标"),
            "pos": meta_field(e["raw"], "词性"),
            "cefr": meta_field(e["raw"], "CEFR"),
            "date": meta_field(e["raw"], "日期"),
            "gloss": re.sub(r"\*\*(.+?)\*\*", r"\1", gloss(e["raw"], level))[:150],
            "blocks": blocks,
            "mistakes": extract_mistakes(e["anchor"], blocks),
        }
        # lets the app tell an edited entry from an untouched one between fetches
        entry["hash"] = digest(entry)
        out.append(entry)
    return out


def digest(obj):
    return hashlib.sha256(json.dumps(obj, ensure_ascii=False, sort_keys=True).encode()).hexdigest()[:16]


def page_data(entries):
    grouped = {}
    for e in entries:
        item = {k: e[k] for k in ("anchor", "title", "ipa", "pos", "cefr", "date", "gloss")}
        item["html"] = to_html(e["blocks"])
        item["level"] = e["level"]
        grouped.setdefault(e["level"], []).append(item)
    return [{"level": l, "items": grouped[l]} for l in GROUP_ORDER if l in grouped]


def mistake_data(entries):
    """The 错题本 view: every tagged mistake, grouped by type, newest entry first."""
    groups = {k: [] for k in TYPE_KEYS}
    for e in sorted(entries, key=lambda e: e["date"].lstrip("~"), reverse=True):
        for m in e["mistakes"]:
            groups[m["type"]].append({
                "sev": m["sev"], "html": inline(m["text"]), "text": m["text"],
                "original": m["original"], "anchor": e["anchor"], "title": e["title"], "date": e["date"],
            })
    return [{"type": k, "desc": d, "items": groups[k]} for k, d in MISTAKE_TYPES if groups[k]]


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

    entries = build_entries((ROOT / "vocabulary.md").read_text(encoding="utf-8"))
    data = page_data(entries)
    mistakes = mistake_data(entries)
    page = (ROOT / "template.html").read_text(encoding="utf-8").replace(
        "/*__DATA__*/", json.dumps(data, ensure_ascii=False)).replace(
        "/*__MISTAKES__*/", json.dumps(mistakes, ensure_ascii=False))
    if "__DATA__" in page or "__MISTAKES__" in page:
        raise SystemExit("template.html is missing its /*__DATA__*/ or /*__MISTAKES__*/ placeholder")

    head = page.split("<!--HEAD-->", 1)[1].split("<!--/HEAD-->", 1)[0].strip()
    body = page.split("<!--/HEAD-->", 1)[1].strip()
    (ROOT / "index.html").write_text(STANDALONE.format(head=head, body=body), encoding="utf-8")

    # The Android app polls meta.json (tiny) and only downloads data.json when
    # the hash differs from what it last fetched. Keep SCHEMA in sync with the app.
    meta = {
        "schema": SCHEMA,
        "hash": digest([e["hash"] for e in entries]),
        "count": len(entries),
        "generated": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }
    (ROOT / "meta.json").write_text(json.dumps(meta, ensure_ascii=False), encoding="utf-8")
    (ROOT / "data.json").write_text(
        json.dumps({**meta, "mistakeTypes": [{"key": k, "desc": d} for k, d in MISTAKE_TYPES],
                    "entries": entries}, ensure_ascii=False), encoding="utf-8")

    if args.artifact:
        pathlib.Path(args.artifact).write_text(
            page.replace("<!--HEAD-->\n", "").replace("<!--/HEAD-->\n", ""), encoding="utf-8")

    total = sum(len(g["items"]) for g in data)
    print(f"{total} entries -> " + ", ".join(f"{g['level']}:{len(g['items'])}" for g in data))
    print(f"{sum(len(g['items']) for g in mistakes)} mistakes -> "
          + ", ".join(f"{g['type']}:{len(g['items'])}" for g in mistakes))


if __name__ == "__main__":
    main()
