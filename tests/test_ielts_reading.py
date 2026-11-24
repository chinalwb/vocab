"""Reading refactors must preserve legacy material and keep other entries unchanged."""
import importlib.util
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("vocab_build", ROOT / "build.py")
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


class IeltsReadingTests(unittest.TestCase):
    def test_metadata_is_not_a_summary_tab(self):
        blocks = build.parse_blocks("- 日期:2026-11-14\n- 类型:IELTS\n- 标签:15-1 · Listening · Part 2\n\n一句概要。\n\n**Original**\n> The original sentence.\n\n**Explanation**\n解释。\n\n**Key words**\n- **word** —— 含义。\n\n**References**\n- [word](#word)")
        sections = build.sections(build.ielts_blocks(blocks))
        self.assertEqual([s[0] for s in sections], ["原文", "读懂这句", "表达卡片", "关联笔记"])
        html = build.to_html(sections[0][1])
        self.assertIn("一句概要。", html)
        self.assertIn("The original sentence.", html)
        self.assertNotIn("标签", html)
        self.assertNotIn("Listening", html)

    def test_legacy_passage_survives_and_normalization_is_idempotent(self):
        blocks = build.parse_blocks("**含义:** 概括。\n\n**原文:**\n> Long original passage.\n\n**题目对应:**\n- Answer B.\n\n**行文逻辑:**\n- A concession.\n\n**词汇与表达:**\n- **bias** —— 立场。")
        result = build.ielts_blocks(blocks)
        html = build.to_html(result)
        for text in ["Long original passage.", "Answer B.", "A concession.", "立场。", "概括。"]:
            self.assertIn(text, html)
        self.assertEqual(result, build.ielts_blocks(result))
        self.assertEqual(len(build.sections(result)), 4)

    def test_real_entries_have_four_sections_and_reciprocal_reference(self):
        entries = build.build_entries((ROOT / "vocabulary.md").read_text())
        ielts = [e for e in entries if e["level"] == "IELTS"]
        for entry in ielts:
            if entry.get("reading"):
                self.assertEqual([s[0] for s in build.sections(entry["blocks"])], ["原文", "读懂这句", "表达卡片", "关联笔记"])
            else:  # 用户 2026-11-24: written like a word entry, so quizzed like one
                self.assertNotIn("原文", [s[0] for s in build.sections(entry["blocks"])])
                self.assertTrue(build.quiz_examples(entry["blocks"]), entry["anchor"])
        entry = next(e for e in ielts if e["anchor"] == "ielts-oldest-children-never-get-over")
        self.assertEqual(entry["tags"], ["15-1", "Listening", "Part 2"])
        table = next(b for b in entry["blocks"] if b["t"] == "table")
        self.assertEqual(len(table["rows"]), 5)
        self.assertTrue(all(len(row) == 8 for row in table["rows"]))
        advent = next(e for e in entries if e["anchor"] == "advent")
        self.assertIn("#ielts-oldest-children-never-get-over", build.to_html(advent["blocks"]))
        self.assertNotEqual(build.sections(advent["blocks"])[0][0], "原文")

    def test_only_the_reading_format_is_read_only(self):
        """用户 2026-11-24: the IELTS group is the source; the Original/Key words format makes it read-only."""
        head = "<a id=\"{a}\"></a>\n## {a}\n\n- 日期:2026-11-24\n- 掌握:会写\n- 类型:IELTS\n\n"
        toc = "## 目录\n\n1. [r](#r)\n2. [w](#w)\n\n---\n\n"
        md = (toc + head.format(a="r") + "**Original**\n> The original sentence.\n\n**Key words**\n- **word** —— 含义。\n\n---\n\n"
              + head.format(a="w") + "**English definition:** To do it.\n\n**含义:** 做。\n\n**例句:**\n\n1. We **do it** daily.\n   我们每天都做。\n")
        entries = {e["anchor"]: e for e in build.build_entries(md)}
        self.assertTrue(entries["r"].get("reading"))
        self.assertNotIn("reading", entries["w"])
        self.assertEqual(entries["w"]["level"], "IELTS")
        self.assertEqual([s[0] for s in build.sections(entries["r"]["blocks"])], ["原文", "读懂这句", "表达卡片", "关联笔记"])
        self.assertEqual(len(build.quiz_examples(entries["w"]["blocks"])), 1)
        items = {i["anchor"]: i for g in build.page_data(list(entries.values())) for i in g["items"]}
        self.assertTrue(items["r"]["reading"])
        self.assertNotIn("reading", items["w"])


if __name__ == "__main__":
    unittest.main()
