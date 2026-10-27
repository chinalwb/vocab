#!/usr/bin/env python3
"""Generate the 词汇便签墙 page from vocabulary.md.

  python build.py                       -> index.html (standalone, for GitHub Pages)
  python build.py --artifact out.html   -> also write the body-only variant used
                                           when publishing as a Claude artifact
  python build.py --src users/taobao -> a student's notebook: reads its vocabulary.md and
                                           site.json, writes the page next to them
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
import sys

ROOT = pathlib.Path(__file__).parent
LEVELS = ["A1", "A2", "B1", "B2", "C1", "C2"]
GROUP_ORDER = LEVELS + ["TERM", "GRAMMAR", "SENTENCE", "KNOWLEDGE", "COLLOC", "SELFTEST"]
SCHEMA = 1  # bump when data.json changes shape in a way the app must know about

# 错题本: a bullet like `- ❌ [介词] **...** —— ...` (or ⚠️) marks a mistake the
# user made. Only the user's own mistakes get a tag; demo counter-examples stay
# untagged. The order here is the order the mistakes view lists them in.
# android/.../ui/Inline.kt renders the same tags — keep both lists in sync.
# The third field is the question the 发送前自检 checklist asks for that type.
MISTAKE_TYPES = [
    ("冠词", "冠词、可数与单复数", "单数可数名词前有没有 a / the / my?特指的东西用了 the 吗?"),
    ("介词", "介词搭配", "关键名词和动词后面的介词,我确定吗?(in situations · on the team · separate from)"),
    ("搭配", "固定搭配与用词", "这个动词 + 名词的组合是固定搭配吗?拿不准就换成更常见的说法。"),
    ("动词", "时态、语态、主谓一致、非谓语", "主语是自己做,还是被做?后面接 to do 还是 -ing?"),
    ("结构", "句型、从句与语序", "疑问句语序、从句引导词对吗?有没有 like that 这类叠用?"),
    ("词形", "拼写、连字符、大小写、比较级", "拼写、连字符(long-term)、副词修饰分词(newly created)、比较级对吗?"),
    ("标点", "逗号粘连等标点问题", "两个完整的句子之间,是不是只放了一个逗号?"),
    ("逻辑", "指代、比较对象、歧义、悬垂修饰", "比较的对象对等吗?代词和省略的主语指代清楚吗?"),
    ("直译", "中式直译与冗余", "句首是不是 For X, … it / they 的话题句?同一个意思说了两遍吗?"),
    ("语气", "语域、正式度与语气轻重", "这个词的正式程度和场合匹配吗?(聊天用 so,别用 therefore)"),
]
TYPE_KEYS = [k for k, *_ in MISTAKE_TYPES]
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


# `- 类型:句子` (or 自测 / 语法) sets the group without the title prefix, so a title can be just
# the point it practises (taobao's notes); the prefixes below still work as before
# Knowledge (用户 2026-10-23): general writing know-how — punctuation, sentence structure — not tied to one word
KIND = {"句子": "SENTENCE", "固定搭配": "COLLOC", "自测": "SELFTEST", "语法": "GRAMMAR", "语域": "GRAMMAR", "Knowledge": "KNOWLEDGE"}


def detect_level(title, raw, own=None):
    """[own]: a notebook's own categories from site.json "groups", {类型 value: group key}."""
    own = own or {}
    kind = meta_field(raw, "类型")
    if kind:
        level = own.get(kind) or next((v for k, v in KIND.items() if kind.startswith(k)), None)
        if not level:
            raise SystemExit(f"{title}: unknown 类型 '{kind}' — use one of {', '.join([*own, *KIND])}")
        return level
    if title.startswith("句子"):
        return "SENTENCE"
    # 翻译: a 中译英 exercise is sentence-level practice, nearest to 整句订正 (用户 2026-10-27)
    if title.startswith("翻译"):
        return "SENTENCE"
    if title.startswith("自测"):
        return "SELFTEST"
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
    # a 自测 card must not give the answer away: its title is the Chinese prompt
    if level == "SELFTEST":
        return "先说出英文,再点开对答案。"
    # a 句子 entry previews best as the original sentence being corrected
    m = re.search(r"^>\s*(.+)$", raw, re.M)
    if m and level == "SENTENCE" and "**我的原句" in raw:
        return m.group(1).strip()
    # a 固定搭配 entry previews as the sentence it was met in: its one numbered example
    m = re.search(r"^1\.\s*(.+)$", raw, re.M)
    if m and level == "COLLOC":
        return re.sub(r"\*\*(.+?)\*\*", r"\1", m.group(1)).strip()
    for pat in (r"\*\*含义[:：]?\*\*[:：]?\s*(.+)", r"\*\*规则[^*]*\*\*[:：]?\s*(.+)"):
        m = re.search(pat, raw)
        if m:
            return m.group(1).strip()
    for line in raw.split("\n"):
        s = line.strip()
        # a bare "**我的原句:**" label says nothing; the quote under it does
        if s and not s.startswith("-") and not s.startswith("**English definition") and not s.startswith("**我的原句"):
            return re.sub(r"\*\*(.+?)\*\*", r"\1", re.sub(r"^>\s*", "", s))
    return ""


def inline(t):
    t = html.escape(t)
    t = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", t)
    t = re.sub(r"~~(.+?)~~", r"<del>\1</del>", t)
    t = re.sub(r"==(.+?)==", r'<mark class="mistake">\1</mark>', t)   # 阅读: a mistake in 我的原句
    t = re.sub(r"`(.+?)`", r"<code>\1</code>", t)
    # 错题本 tags first, so a tag is never swallowed into a later [text](#anchor)
    t = re.sub(r"\[(" + "|".join(TYPE_KEYS) + r")\](?!\()", r'<span class="mtag">\1</span>', t)
    # cross-references become real links the page turns into card-to-card jumps
    t = re.sub(r"\[(.+?)\]\(#([^)]+)\)", r'<a class="xref" href="#\2" data-ref="\2">\1</a>', t)
    t = re.sub(r"(?<![\w*])\*([^*\n]+)\*(?![\w*])", r"<em>\1</em>", t)
    return t


def strip_meta(raw):
    # the 音标/词性/CEFR/日期/掌握/类型 bullets are rendered as chips, not body text
    return re.sub(r"^(?:-\s*(?:音标|词性|CEFR|日期|掌握|类型|关键词)[:：][^\n]*\n?)+", "", raw.strip(), flags=re.M).strip()


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


# A bold label opens a section: "**常见搭配:**", "**含义:** text", "**义项一:…**", "**race**(动词)".
SECTION_RE = re.compile(r"^\*\*([^*]+?)[:：]\s*\*\*|^\*\*([^*]+?)\*\*\s*(?:[(（][^)）]*[)）])?$")
# consecutive definition sections share one card
DEFINITION = ("English definition", "含义", "释义", "整体释义")


def section_label(text):
    """The short name a section's tab shows: "义项一:纠结…" -> "义项一"."""
    m = SECTION_RE.match(text)
    if not m:
        return None
    return re.split(r"[:：(（—]", (m.group(1) or m.group(2)).replace("⚠️", "").strip())[0].strip()


def sections(blocks):
    """Split an entry's blocks at its bold labels: [(label, [blocks]), ...].

    The notes show one section per card (a carousel), so a glance never has to take in the
    whole entry. Whatever comes before the first label is 概要. The app splits the same way
    (sections() in EntryBody.kt) — keep the two in step.
    """
    out = []
    for b in blocks:
        label = section_label(b["text"]) if b["t"] == "p" else None
        if label and label in DEFINITION and out and out[-1][0] == "释义":
            label = None                       # English definition + 含义 → one 释义 card
        elif label in DEFINITION:
            label = "释义"
        if label or not out:
            label = label or "概要"
            out.append((label, []))           # the page cuts a long one short (.nv-tab max-width)
        out[-1][1].append(b)
    return out


def mastery(raw, level):
    """会写 → "write" (practise producing it), 认识 → "read" (recognising is enough)."""
    v = meta_field(raw, "掌握")
    if v:
        return "write" if v.startswith("会写") else "read"
    return "write" if level in ("SENTENCE", "COLLOC", "GRAMMAR", "SELFTEST") else "read"


def quote_after(blocks, label):
    """The quote that follows a **label** paragraph (e.g. 我的原句, 答案), or ""."""
    want = False
    for b in blocks:
        if b["t"] == "p" and b["text"].startswith("**" + label):
            want = True
        elif b["t"] == "quote" and want:
            return "\n".join(b["lines"])
    return ""


def original_sentence(blocks):
    return quote_after(blocks, "我的原句")


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


def toc_numbers(src):
    """anchor -> its number in the ## 目录 list; that number is the entry's index (#12)."""
    return {a: int(n) for n, a in re.findall(r"^(\d+)\. \[.*?\]\(#([^)]+)\)\s*$", src, re.M)}


def build_entries(src, own=None):
    out = []
    numbers = toc_numbers(src)
    for e in split_entries(src):
        if e["anchor"] not in numbers:
            raise SystemExit(f"{e['anchor']}: missing from ## 目录 — add a 'N. [title](#{e['anchor']})' line")
        level = detect_level(e["title"], e["raw"], own)
        # 术语 is also the fallback: an entry with no 类型, 词性 or CEFR that lands there is
        # probably a new kind of entry no rule knows yet — say so instead of filing it quietly
        if level == "TERM" and not meta_field(e["raw"], "词性") and not re.search(r"CEFR[:：]", e["raw"]):
            print(f"warning: {e['title']} ({e['anchor']}) has no 类型, 词性 or CEFR — filed as 术语; "
                  "add a '- 类型:' line or a title prefix rule", file=sys.stderr)
        blocks = parse_blocks(e["raw"])
        entry = {
            "anchor": e["anchor"],
            "no": numbers[e["anchor"]],
            "title": e["title"],
            "level": level,
            "ipa": meta_field(e["raw"], "音标"),
            "pos": meta_field(e["raw"], "词性"),
            "cefr": meta_field(e["raw"], "CEFR"),
            "date": meta_field(e["raw"], "日期"),
            "mastery": mastery(e["raw"], level),
            "gloss": re.sub(r"\*\*(.+?)\*\*", r"\1", gloss(e["raw"], level))[:150],
            "blocks": blocks,
            "mistakes": extract_mistakes(e["anchor"], blocks),
        }
        # lets the app tell an edited entry from an untouched one between fetches
        entry["hash"] = digest(entry)
        out.append(entry)
    return out


def build_reads(src):
    """阅读 (readlist.md): read-only pairs of a common and a C1 way to say the same thing.

    Not entries: no level, no quiz, no 进度, no 错题本. Newest (last in the file) first.
    """
    out = []
    for e in split_entries(src):
        blocks = parse_blocks(e["raw"])
        # 我的原句 goes last and folded on the detail page, so it ships apart from the rest
        cut = next((i for i, b in enumerate(blocks) if b["t"] == "p" and section_label(b["text"]) == "我的原句"), len(blocks))
        item = {"anchor": e["anchor"], "title": e["title"], "date": meta_field(e["raw"], "日期"),
                "keywords": [k.strip() for k in meta_field(e["raw"], "关键词").split("·") if k.strip()],
                "blocks": blocks[:cut], "original": [l for b in blocks[cut + 1:] if b["t"] == "quote" for l in b["lines"]],
                # one bullet per ==highlighted== mistake: what it should be and why
                "originalNotes": [it for b in blocks[cut + 1:] if b["t"] == "ul" for it in b["items"]]}
        item["hash"] = digest(item)
        out.append(item)
    seen = set()
    for r in out:
        if r["anchor"] in seen:
            raise SystemExit(f"readlist.md: duplicate anchor {r['anchor']}")
        seen.add(r["anchor"])
    return out[::-1]


def quiz_examples(blocks):
    """Numbered examples that have both an English sentence and a Chinese translation."""
    out = []
    for b in blocks:
        if b["t"] == "ol":
            for it in b["items"]:
                if it["zh"] and re.search(r"[A-Za-z]", it["en"]) and re.search(r"[\u4e00-\u9fff]", it["zh"]):
                    out.append({"en": it["en"], "zh": it["zh"]})
    return out


def digest(obj):
    return hashlib.sha256(json.dumps(obj, ensure_ascii=False, sort_keys=True).encode()).hexdigest()[:16]


def page_data(entries, order=GROUP_ORDER):
    grouped = {}
    for e in entries:
        item = {k: e[k] for k in ("anchor", "no", "title", "ipa", "pos", "cefr", "date", "gloss", "mastery")}
        item["original"] = original_sentence(e["blocks"])
        if e["level"] == "SELFTEST":
            item["answer"] = quote_after(e["blocks"], "答案")
        else:
            # an entry moved to 自测 is quizzed on its example sentences: Chinese → English
            item["examples"] = quiz_examples(e["blocks"])
        # the page joins these back into the whole notes for search and the 全部展开 view
        item["slides"] = [[label, to_html(bs)] for label, bs in sections(e["blocks"])]
        item["level"] = e["level"]
        grouped.setdefault(e["level"], []).append(item)
    return [{"level": l, "items": grouped[l]} for l in order if l in grouped]


def mistake_data(entries):
    """The 错题本 view: every tagged mistake, grouped by type, newest entry first."""
    groups = {k: [] for k in TYPE_KEYS}
    for e in sorted(entries, key=lambda e: e["date"].lstrip("~"), reverse=True):
        for m in e["mistakes"]:
            groups[m["type"]].append({
                "sev": m["sev"], "html": inline(m["text"]), "text": m["text"],
                "original": m["original"], "anchor": e["anchor"], "no": e["no"], "title": e["title"], "date": e["date"],
            })
    return [{"type": k, "desc": d, "check": c, "items": groups[k]} for k, d, c in MISTAKE_TYPES if groups[k]]


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


# The page's VK config (template.html). A notebook's site.json overrides any of it; a student's
# needs its own prefix (same origin, so localStorage is shared) and its own sync file.
SITE = {
    "title": "词汇便签墙",
    "brand": "VoCab",
    "prefix": "vk.",
    "app": True,                     # the Android app reads this notebook too
    "categories": True,              # 分类 in the sidebar, 类型 / 级别 rows in 筛选
    "cardColor": "level",            # or "stage": cards coloured by 学习中 / 复习中 / 考核中 / 已掌握
    "logo": "",                      # a picture next to the page's index.html, shown in front of the name
    "fillBlanks": True,              # 打字 asks only for the **bold** part of a quiz sentence, the rest filled in
    # a notebook's own categories (用户 2026-10-23, so's): [{"kind": the `- 类型:` value, "key": the group
    # key (A–Z), "label": its short name, "name": its section heading, "swatch": a palette in template.html
    # (a1 a2 b1 b2 c1 c2 tm gr sn cl st)}]. With any, 分类 and the 类型 row list only these.
    "groups": [],
    "sync": {"repo": "chinalwb/vocab", "branch": "progress", "path": "progress.json"},
    # optional colour overrides: {"light": {"--accent": "#…", …}, "dark": {…}}, laid over the
    # template's own :root blocks (light, dark by system setting, dark by the theme button)
    "theme": {},
}


def theme_css(theme):
    decl = lambda d: "".join(f"{k}:{v};" for k, v in d.items())
    css = ""
    if theme.get("light"):
        css += f":root{{{decl(theme['light'])}}}\n"
    if theme.get("dark"):
        css += (f"@media (prefers-color-scheme: dark){{ :root:not([data-theme=\"light\"]){{{decl(theme['dark'])}}} }}\n"
                f":root[data-theme=\"dark\"]{{{decl(theme['dark'])}}}\n")
    return f"<style>\n{css}</style>\n" if css else ""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--artifact", metavar="PATH", help="also write the body-only variant here")
    ap.add_argument("--src", metavar="DIR", help="the notebook to build (default: the repo root)")
    args = ap.parse_args()

    src = (ROOT / args.src).resolve() if args.src else ROOT
    site = dict(SITE)
    if (src / "site.json").exists():
        site.update(json.loads((src / "site.json").read_text(encoding="utf-8")))
    if src != ROOT and site["prefix"] == SITE["prefix"]:
        raise SystemExit(f"{src / 'site.json'}: needs its own \"prefix\" — it shares localStorage with the main page")
    if site["logo"] and not (src / site["logo"]).exists():
        raise SystemExit(f"{src / 'site.json'}: logo {site['logo']} is missing")
    if src != ROOT and site["sync"]["path"] == SITE["sync"]["path"]:
        raise SystemExit(f"{src / 'site.json'}: needs its own sync \"path\" — it would overwrite progress.json")

    swatches = {"a1", "a2", "b1", "b2", "c1", "c2", "tm", "gr", "sn", "cl", "st"}
    for g in site["groups"]:
        if not re.fullmatch(r"[A-Z]+", g.get("key", "")) or g["key"] in GROUP_ORDER or g.get("swatch") not in swatches or not g.get("kind"):
            raise SystemExit(f"{src / 'site.json'}: group {g} needs a kind, a new key in A–Z and a swatch from {sorted(swatches)}")
    own = {g["kind"]: g["key"] for g in site["groups"]}
    entries = build_entries((src / "vocabulary.md").read_text(encoding="utf-8"), own)
    read_md = src / "readlist.md"
    reads = build_reads(read_md.read_text(encoding="utf-8")) if read_md.exists() else []
    data = page_data(entries, [g["key"] for g in site["groups"]] + GROUP_ORDER)
    mistakes = mistake_data(entries)
    page = (ROOT / "template.html").read_text(encoding="utf-8").replace(
        "/*__DATA__*/", json.dumps(data, ensure_ascii=False)).replace(
        "/*__MISTAKES__*/", json.dumps(mistakes, ensure_ascii=False)).replace(
        "/*__CONFIG__*/", json.dumps(site, ensure_ascii=False))
    page = page.replace("<!--/HEAD-->", theme_css(site["theme"]) + "<!--/HEAD-->", 1)
    if "__DATA__" in page or "__MISTAKES__" in page or "__CONFIG__" in page:
        raise SystemExit("template.html is missing its /*__DATA__*/, /*__MISTAKES__*/ or /*__CONFIG__*/ placeholder")

    head = page.split("<!--HEAD-->", 1)[1].split("<!--/HEAD-->", 1)[0].strip()
    body = page.split("<!--/HEAD-->", 1)[1].strip()
    (src / "index.html").write_text(STANDALONE.format(head=head, body=body), encoding="utf-8")

    # The Android app polls meta.json (tiny) and only downloads data.json when
    # the hash differs from what it last fetched. Keep SCHEMA in sync with the app.
    meta = {
        "schema": SCHEMA,
        # reads count too, so a new 阅读 item alone makes the app fetch data.json again
        "hash": digest([e["hash"] for e in entries] + [r["hash"] for r in reads]),
        "count": len(entries),
        "generated": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }
    (src / "meta.json").write_text(json.dumps(meta, ensure_ascii=False), encoding="utf-8")
    (src / "data.json").write_text(
        json.dumps({**meta, "mistakeTypes": [{"key": k, "desc": d, "check": c} for k, d, c in MISTAKE_TYPES],
                    "entries": entries, "reads": reads}, ensure_ascii=False), encoding="utf-8")

    if args.artifact:
        pathlib.Path(args.artifact).write_text(
            page.replace("<!--HEAD-->\n", "").replace("<!--/HEAD-->\n", ""), encoding="utf-8")

    total = sum(len(g["items"]) for g in data)
    print(f"{total} entries -> " + ", ".join(f"{g['level']}:{len(g['items'])}" for g in data))
    print(f"{sum(len(g['items']) for g in mistakes)} mistakes -> "
          + ", ".join(f"{g['type']}:{len(g['items'])}" for g in mistakes))
    print(f"{len(reads)} reads (readlist.md)")
    if src != ROOT:
        print(f"-> {(src / 'index.html').relative_to(ROOT)}")


if __name__ == "__main__":
    main()
