#!/usr/bin/env python3
"""Measures how accurately an extractor turns technicians' service notes into service records.

The test set (notes.jsonl) holds synthetic notes, written the way a technician dictates or
texts, with the fields a correct extraction must produce. Each note is scored field by field:

  kind            exact match (some ambiguous notes accept an alternative)
  follow-up       is a follow-up needed? (yes/no)
  parts           precision and recall; a predicted part that wasn't used is a hallucination
  readings        precision and recall on (name, value) pairs
  work done       share of key terms the summary mentions
  follow-up note  share of key terms the note mentions (only where a follow-up is needed)

Predictions come from one of three sources:
  --predictions FILE   JSONL lines: {"id": "n01", "prediction": {...}}
  --endpoint URL       POSTs {"note", "equipmentType"} to an extraction API (with --token)
  --baseline           a simple keyword extractor, to check the scorer and set a floor

With --check, exits with status 1 if any score is below thresholds.json. That's the gate the
AI feature (S13) must pass before it ships. Standard library only.
"""
import argparse
import json
import re
import sys
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent


# --- Matching ---------------------------------------------------------------------------

def normalize(text):
    """Lower-case; drop hyphens and most punctuation so 'R-410A' matches 'r410a'."""
    text = (text or "").lower().replace("-", "")
    text = re.sub(r"[^a-z0-9./ ]", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def mentions(text, term):
    """True if text contains the term; 'a|b' in a term means either is fine."""
    text = normalize(text)
    return any(normalize(option) in text for option in term.split("|"))


def part_matches(predicted, expected):
    return all(mentions(predicted, word) for word in expected["match"])


def reading_matches(predicted, expected):
    try:
        value = float(predicted.get("value"))
    except (TypeError, ValueError):
        return False
    name = normalize(predicted.get("name"))
    named = any(normalize(alias) == name or normalize(alias) in name.split() or normalize(alias) in name
                for alias in expected["aliases"])
    tolerance = max(abs(expected["value"]) * 0.01, 0.011)
    return named and abs(value - expected["value"]) <= tolerance


def match_lists(predicted, expected, matches):
    """Greedy one-to-one matching. Returns (true positives, false positives, false negatives)."""
    unused = list(expected)
    true_pos = 0
    for item in predicted:
        hit = next((e for e in unused if matches(item, e)), None)
        if hit is not None:
            unused.remove(hit)
            true_pos += 1
    return true_pos, len(predicted) - true_pos, len(unused)


def term_recall(text, terms):
    if not terms:
        return None
    return sum(mentions(text, t) for t in terms) / len(terms)


# --- Scoring ----------------------------------------------------------------------------

def score_note(note, prediction):
    """Per-note results; a missing or malformed prediction scores as all wrong."""
    exp = note["expected"]
    p = prediction if isinstance(prediction, dict) else {}
    parts = [x for x in (p.get("partsUsed") or []) if isinstance(x, str) and x.strip()]
    readings = [x for x in (p.get("readings") or []) if isinstance(x, dict)]

    parts_tp, parts_fp, parts_fn = match_lists(parts, exp["parts"], part_matches)
    read_tp, read_fp, read_fn = match_lists(readings, exp["readings"], reading_matches)

    return {
        "id": note["id"],
        "kind": p.get("kind") in [exp["kind"], *exp.get("kindAlso", [])],
        "followUp": p.get("followUpNeeded") is exp["followUpNeeded"],
        "parts": (parts_tp, parts_fp, parts_fn),
        "readings": (read_tp, read_fp, read_fn),
        "workDone": term_recall(p.get("workDone") or "", exp["workDoneTerms"]),
        "followUpNote": term_recall(p.get("followUpNote") or "", exp["followUpTerms"])
        if exp["followUpNeeded"] else None,
        "hallucinatedParts": [x for x in parts if not any(part_matches(x, e) for e in exp["parts"])],
    }


def ratio(numerator, denominator):
    return numerator / denominator if denominator else 1.0


def summarize(results):
    def totals(field):
        tp = sum(r[field][0] for r in results)
        fp = sum(r[field][1] for r in results)
        fn = sum(r[field][2] for r in results)
        return ratio(tp, tp + fp), ratio(tp, tp + fn)

    parts_precision, parts_recall = totals("parts")
    readings_precision, readings_recall = totals("readings")
    work = [r["workDone"] for r in results if r["workDone"] is not None]
    follow = [r["followUpNote"] for r in results if r["followUpNote"] is not None]
    return {
        "kind_accuracy": ratio(sum(r["kind"] for r in results), len(results)),
        "follow_up_accuracy": ratio(sum(r["followUp"] for r in results), len(results)),
        "parts_precision": parts_precision,
        "parts_recall": parts_recall,
        "readings_precision": readings_precision,
        "readings_recall": readings_recall,
        "work_done_term_recall": ratio(sum(work), len(work)),
        "follow_up_note_term_recall": ratio(sum(follow), len(follow)),
    }


# --- Prediction sources -------------------------------------------------------------------

def baseline(note, _equipment_type):
    """Deliberately simple keyword rules: a floor any real extractor must clearly beat."""
    text = note.lower()
    if re.search(r"\binstall", text):
        kind = "INSTALLATION"
    elif re.search(r"tune|annual|start ?up|spring service", text):
        kind = "TUNE_UP"
    elif re.search(r"inspection|safety check|checked everything", text):
        kind = "INSPECTION"
    else:
        kind = "REPAIR"
    parts = [m.group(2).strip() for m in re.finditer(
        r"\b(replaced|new|changed|swapped in|put in)\s+(?:the |a |an )?([a-z0-9 ./]+?)(?:[,.]| and |$)", text)]
    readings = []
    for name, pattern in [("CO", r"\bco (?:was |in the flue )?(\d+(?:\.\d+)?)"),
                          ("Superheat", r"superheat (\d+(?:\.\d+)?)"),
                          ("Suction", r"suction (\d+(?:\.\d+)?)"),
                          ("Pressure", r"pressure (?:back to )?(\d+(?:\.\d+)?) psi")]:
        m = re.search(pattern, text)
        if m:
            readings.append({"name": name, "value": float(m.group(1)), "unit": ""})
    follow = bool(re.search(r"recommend|come back|coming back|need to|should|quote|keep an eye|get back", text))
    return {"kind": kind, "workDone": note, "partsUsed": parts, "readings": readings,
            "followUpNeeded": follow, "followUpNote": note if follow else None}


def from_endpoint(url, token):
    def extract(note, equipment_type):
        request = urllib.request.Request(url, method="POST",
                                         data=json.dumps({"note": note, "equipmentType": equipment_type}).encode(),
                                         headers={"Content-Type": "application/json",
                                                  "Authorization": f"Bearer {token}"})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response)
        except Exception as error:  # a failed call scores as a wrong answer, not a crash
            print(f"  request failed: {error}", file=sys.stderr)
            return None
    return extract


def load_jsonl(path):
    return [json.loads(line) for line in Path(path).read_text().splitlines() if line.strip()]


# --- Report -------------------------------------------------------------------------------

LABELS = {
    "kind_accuracy": "Kind of visit",
    "follow_up_accuracy": "Follow-up needed (yes/no)",
    "parts_precision": "Parts precision (no hallucinations)",
    "parts_recall": "Parts recall (nothing missed)",
    "readings_precision": "Readings precision",
    "readings_recall": "Readings recall",
    "work_done_term_recall": "Work done key terms",
    "follow_up_note_term_recall": "Follow-up note key terms",
}


def report(summary, thresholds):
    failed = []
    print(f"{'Field':40} {'Score':>7} {'Needed':>7}")
    for key, label in LABELS.items():
        needed = thresholds.get(key)
        ok = needed is None or summary[key] >= needed
        if not ok:
            failed.append(key)
        print(f"{label:40} {summary[key]:7.1%} {needed:7.0%} {'' if ok else '  BELOW'}")
    return failed


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--predictions", help="JSONL file of {id, prediction}")
    source.add_argument("--endpoint", help="extraction API URL")
    source.add_argument("--baseline", action="store_true", help="score the keyword baseline")
    parser.add_argument("--token", help="bearer token for --endpoint")
    parser.add_argument("--notes", default=HERE / "notes.jsonl")
    parser.add_argument("--thresholds", default=HERE / "thresholds.json")
    parser.add_argument("--check", action="store_true", help="exit 1 if any score is below its threshold")
    parser.add_argument("--details", action="store_true", help="list every miss, note by note")
    args = parser.parse_args(argv)

    notes = load_jsonl(args.notes)
    if args.predictions:
        by_id = {row["id"]: row.get("prediction") for row in load_jsonl(args.predictions)}

        def predict(note):
            return by_id.get(note["id"])
    else:
        extract = from_endpoint(args.endpoint, args.token) if args.endpoint else baseline

        def predict(note):
            return extract(note["note"], note["equipmentType"])

    results = [score_note(note, predict(note)) for note in notes]

    thresholds = json.loads(Path(args.thresholds).read_text())
    thresholds = {k: v for k, v in thresholds.items() if not k.startswith("_")}
    print(f"{len(notes)} notes\n")
    failed = report(summarize(results), thresholds)

    hallucinated = [(r["id"], p) for r in results for p in r["hallucinatedParts"]]
    print(f"\nHallucinated parts: {len(hallucinated)}")
    if args.details:
        for r in results:
            misses = [name for name in ("kind", "followUp") if not r[name]]
            if r["parts"][1] or r["parts"][2]:
                misses.append(f"parts +{r['parts'][1]}/-{r['parts'][2]}")
            if r["readings"][1] or r["readings"][2]:
                misses.append(f"readings +{r['readings'][1]}/-{r['readings'][2]}")
            if misses:
                print(f"  {r['id']}: {', '.join(misses)}")

    if args.check and failed:
        print(f"\nFAIL: below threshold on {', '.join(failed)}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
