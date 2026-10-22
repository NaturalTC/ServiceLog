"""Tests for the scorer itself: if these are wrong, every accuracy number is wrong.

Run from ai-eval/:  python3 -m unittest -v
"""
import json
import unittest
from pathlib import Path

import evaluate

NOTES = evaluate.load_jsonl(Path(__file__).parent / "notes.jsonl")


def perfect_prediction(note):
    """What an ideal extractor would return for a note, built from its expected fields."""
    exp = note["expected"]
    return {
        "kind": exp["kind"],
        "workDone": " ".join(t.split("|")[0] for t in exp["workDoneTerms"]),
        "partsUsed": [p["name"] for p in exp["parts"]],
        "readings": [{"name": r["aliases"][0], "value": r["value"], "unit": r["unit"]} for r in exp["readings"]],
        "followUpNeeded": exp["followUpNeeded"],
        "followUpNote": " ".join(exp["followUpTerms"]) or None,
    }


class TestDataset(unittest.TestCase):

    def test_hasAtLeast30Notes_withUniqueIds(self):
        self.assertGreaterEqual(len(NOTES), 30)
        self.assertEqual(len(NOTES), len({n["id"] for n in NOTES}))

    def test_everyExpectedPartMatchesItsOwnName(self):
        # Catches annotation mistakes, e.g. a match word that isn't in the part's name
        for note in NOTES:
            for part in note["expected"]["parts"]:
                self.assertTrue(evaluate.part_matches(part["name"], part), f"{note['id']}: {part}")

    def test_kindsAreValid(self):
        valid = {"TUNE_UP", "REPAIR", "INSTALLATION", "INSPECTION", "OTHER"}
        for note in NOTES:
            self.assertIn(note["expected"]["kind"], valid)
            self.assertTrue(set(note["expected"].get("kindAlso", [])) <= valid)


class TestScoring(unittest.TestCase):

    def summary_for(self, predictions):
        return evaluate.summarize([evaluate.score_note(n, predictions.get(n["id"])) for n in NOTES])

    def test_perfectPredictions_scoreEverythingAt100Percent(self):
        summary = self.summary_for({n["id"]: perfect_prediction(n) for n in NOTES})
        for key, value in summary.items():
            self.assertEqual(value, 1.0, key)

    def test_perfectPredictions_passTheThresholds(self):
        predictions = Path(self.id().replace(".", "_") + ".jsonl")
        try:
            predictions.write_text("".join(json.dumps({"id": n["id"], "prediction": perfect_prediction(n)}) + "\n"
                                           for n in NOTES))
            self.assertEqual(evaluate.main(["--predictions", str(predictions), "--check"]), 0)
        finally:
            predictions.unlink(missing_ok=True)

    def test_missingPredictions_scoreAsWrong_notAsACrash(self):
        summary = self.summary_for({})
        self.assertEqual(summary["kind_accuracy"], 0.0)
        self.assertEqual(summary["parts_recall"], 0.0)

    def test_inventedPart_isAHallucination_thatLowersPrecisionOnly(self):
        note = next(n for n in NOTES if n["id"] == "n27")  # pressure switch brought but not used
        prediction = perfect_prediction(note) | {"partsUsed": ["pressure switch"]}

        result = evaluate.score_note(note, prediction)

        self.assertEqual(result["parts"], (0, 1, 0))
        self.assertEqual(result["hallucinatedParts"], ["pressure switch"])

    def test_missedPart_lowersRecall(self):
        note = next(n for n in NOTES if n["id"] == "n01")
        result = evaluate.score_note(note, perfect_prediction(note) | {"partsUsed": ["nozzle"]})

        self.assertEqual(result["parts"], (1, 0, 1))

    def test_ambiguousNote_acceptsTheAlternativeKind(self):
        note = next(n for n in NOTES if n["id"] == "n31")
        self.assertTrue(evaluate.score_note(note, perfect_prediction(note) | {"kind": "REPAIR"})["kind"])
        self.assertFalse(evaluate.score_note(note, perfect_prediction(note) | {"kind": "INSTALLATION"})["kind"])


class TestMatching(unittest.TestCase):

    def test_normalize_handlesHyphensCaseAndPunctuation(self):
        self.assertEqual(evaluate.normalize("8 oz R-410A!"), "8 oz r410a")
        self.assertTrue(evaluate.mentions("Added 8 oz of R-410A", "r410a"))

    def test_termAlternatives(self):
        self.assertTrue(evaluate.mentions("Tested the relief valve", "t&p|relief valve"))
        self.assertTrue(evaluate.mentions("Tested the T&P", "t&p|relief valve"))
        self.assertFalse(evaluate.mentions("Tested the burner", "t&p|relief valve"))

    def test_reading_needsTheRightNameAndValue(self):
        expected = {"aliases": ["co", "carbon monoxide"], "value": 12, "unit": "ppm"}
        self.assertTrue(evaluate.reading_matches({"name": "CO", "value": 12}, expected))
        self.assertTrue(evaluate.reading_matches({"name": "Carbon monoxide", "value": "12.0"}, expected))
        self.assertFalse(evaluate.reading_matches({"name": "CO", "value": 21}, expected))
        self.assertFalse(evaluate.reading_matches({"name": "Draft", "value": 12}, expected))
        self.assertFalse(evaluate.reading_matches({"name": "CO", "value": "twelve"}, expected))

    def test_reading_allowsSmallRounding(self):
        expected = {"aliases": ["draft"], "value": -0.02, "unit": "in wc"}
        self.assertTrue(evaluate.reading_matches({"name": "draft", "value": -0.02}, expected))
        self.assertTrue(evaluate.reading_matches({"name": "overfire draft", "value": -0.025}, expected))

    def test_eachExpectedItemMatchesAtMostOnce(self):
        expected = [{"name": "Nozzle", "match": ["nozzle"]}]
        self.assertEqual(evaluate.match_lists(["nozzle", "nozzle"], expected, evaluate.part_matches), (1, 1, 0))


if __name__ == "__main__":
    unittest.main()
