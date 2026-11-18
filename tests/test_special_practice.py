"""Validate authored drills and their isolation from ordinary lesson quizzes."""
import copy
import importlib.util
import json
import pathlib
import tempfile
import subprocess
import sys
import unittest
from collections import Counter

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("vocab_build", ROOT / "build.py")
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


class SpecialPracticeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = ROOT / "users/taobao"
        subprocess.run([sys.executable, str(ROOT / "build.py"), "--src", str(cls.source)], check=True, stdout=subprocess.DEVNULL)
        cls.notes = build.build_entries((cls.source / "vocabulary.md").read_text())
        cls.payload = json.loads((cls.source / "practices.json").read_text())

    def parse(self, payload):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "practices.json"
            path.write_text(json.dumps(payload))
            return build.build_practices(path, self.notes)

    def test_28_questions_are_balanced_and_all_core_notes_are_practicable(self):
        entries = self.parse(self.payload)
        self.assertEqual(len(entries), 1)
        entry = entries[0]
        questions = entry["practice"]["questions"]
        self.assertEqual(len(questions), 28)
        self.assertEqual(Counter(q["skill"] for q in questions), dict.fromkeys(self.payload[0]["skills"], 7))
        self.assertEqual({q["type"] for q in questions}, {"fill", "correct", "rewrite"})
        self.assertEqual(build.quiz_examples(entry["blocks"]), [])
        for anchor in {q["note"] for q in questions}:
            note = next(n for n in self.notes if n["anchor"] == anchor)
            self.assertEqual(len(build.quiz_examples(note["blocks"])), 2)
            self.assertIn("#" + entry["anchor"], build.to_html(note["blocks"]))

    def test_question_ids_types_answers_and_core_references_are_validated(self):
        variants = []
        p = copy.deepcopy(self.payload)
        p[0]["questions"][1]["id"] = p[0]["questions"][0]["id"]
        variants.append(p)
        for key, value in [("skill", "unknown"), ("type", "unknown"), ("answers", []), ("note", "missing-note"), ("hint", "")]:
            p = copy.deepcopy(self.payload)
            p[0]["questions"][0][key] = value
            variants.append(p)
        for p in variants:
            with self.subTest(value=p[0]["questions"][0]):
                with self.assertRaises(ValueError):
                    self.parse(p)

    def test_practice_anchors_cannot_collide_and_content_changes_change_hash(self):
        p = copy.deepcopy(self.payload)
        p.append(copy.deepcopy(p[0]))
        with self.assertRaises(ValueError):
            self.parse(p)
        initial = self.parse(self.payload)[0]["hash"]
        p = copy.deepcopy(self.payload)
        p[0]["questions"][0]["answers"] = ["different answer"]
        self.assertNotEqual(initial, self.parse(p)[0]["hash"])

    def test_page_payload_keeps_questions_out_of_ordinary_examples(self):
        practice = self.parse(self.payload)
        groups = build.page_data(self.notes + practice)
        item = next(g for g in groups if g["level"] == "PRACTICE")["items"][0]
        self.assertEqual(len(item["practice"]["questions"]), 28)
        self.assertEqual(item["examples"], [])
        self.assertIsNone(item["no"])
        native = json.loads((self.source / "data.json").read_text())
        self.assertFalse(any(e["level"] == "PRACTICE" for e in native["entries"]))
        self.assertEqual(len(native["practices"]), 1)
        self.assertEqual(len(native["practices"][0]["practice"]["questions"]), 28)
        self.assertEqual(native["schema"], 1)  # Older clients ignore the optional collection.

    def test_native_meta_hash_includes_question_only_updates(self):
        native = json.loads((self.source / "data.json").read_text())
        prefix = [e["hash"] for e in native["entries"]] + [r["hash"] for r in native["reads"]]
        hashes = [p["hash"] for p in native["practices"]]
        self.assertEqual(native["hash"], build.digest(prefix + hashes))
        changed = copy.deepcopy(self.payload)
        changed[0]["questions"][0]["hint"] += " Updated hint."
        new_hash = self.parse(changed)[0]["hash"]
        self.assertNotEqual(native["hash"], build.digest(prefix + [new_hash]))


if __name__ == "__main__":
    unittest.main()
