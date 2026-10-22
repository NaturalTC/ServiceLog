"""Tests for the nameplate scorer (nameplates/evaluate_nameplates.py)."""
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "nameplates"))
import evaluate_nameplates as ev  # noqa: E402

PLATE = {"readable": True, "brand": "Navien", "model": "NCB-240E", "serial": "7A1B2C3D", "manufactured": None}
WALL = {"readable": False, "brand": None, "model": None, "serial": None, "manufactured": None}


class ScoreTest(unittest.TestCase):

    def test_exact_reads_score_full_marks_ignoring_case_and_spaces(self):
        m = ev.score([PLATE], [{"readable": True, "brand": "navien", "model": "NCB - 240E", "serialNumber": "7a1b2c3d"}])
        self.assertEqual((m["brand_accuracy"], m["model_accuracy"], m["serial_accuracy"]), (1.0, 1.0, 1.0))
        self.assertEqual(m["wrong_values"], 0)

    def test_a_misread_serial_is_a_wrong_value_but_a_blank_is_not(self):
        misread = ev.score([PLATE], [{"readable": True, "brand": "Navien", "model": "NCB-240E", "serialNumber": "7A1B2C3B"}])
        blank = ev.score([PLATE], [{"readable": True, "brand": "Navien", "model": "NCB-240E", "serialNumber": None}])
        self.assertEqual(misread["wrong_values"], 1)
        self.assertEqual(blank["wrong_values"], 0)
        self.assertEqual(blank["serial_accuracy"], 0.0)

    def test_anything_returned_for_an_unreadable_photo_counts_as_wrong(self):
        m = ev.score([WALL], [{"readable": True, "brand": "Carrier", "model": "24ABC", "serialNumber": None}])
        self.assertEqual(m["wrong_values"], 1)
        self.assertEqual(m["unreadable_flagged"], 0.0)
        self.assertEqual(ev.score([WALL], [{"readable": False}])["unreadable_flagged"], 1.0)

    def test_truth_file_matches_the_plates(self):
        here = Path(__file__).parent / "nameplates"
        truth = [json.loads(line) for line in (here / "truth.jsonl").read_text().splitlines() if line.strip()]
        self.assertEqual(len(truth), 12)
        for t in truth:
            self.assertTrue((here / "plates" / t["file"]).exists(), t["file"])
        self.assertEqual(sum(not t["readable"] for t in truth), 2)


if __name__ == "__main__":
    unittest.main()
