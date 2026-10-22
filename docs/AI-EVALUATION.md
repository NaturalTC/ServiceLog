# Measuring the AI service-note feature

S13 lets the owner dictate notes like *"annual on the beckett, changed the nozzle and filter,
CO 12"* and have AI fill in the service record. Before that ships, this test set measures
how accurately an extractor does it. The feature only ships if it clears the bar below.

## The extraction contract
Given a note and the equipment type, an extractor returns:

```json
{
  "kind": "TUNE_UP | REPAIR | INSTALLATION | INSPECTION | OTHER",
  "workDone": "short summary of the work",
  "partsUsed": ["Nozzle 0.75 80A", "Oil filter"],
  "readings": [{ "name": "CO", "value": 12, "unit": "ppm" }],
  "followUpNeeded": false,
  "followUpNote": null
}
```

Readings (CO, draft, pressures, superheat, temperature split...) have no field of their own
in a service record yet; S13 will put them in the record's notes.

## The test set (`ai-eval/notes.jsonl`)
36 **synthetic** notes, written the way technicians dictate or text: shorthand
("nozzle filter strainer. CO 10."), filler ("Uh so this was the annual"), typos and missing
punctuation, across all eight equipment types and all four kinds of visit. No real customer
data. Each note lists the fields a correct extraction must produce.

Deliberate traps, because these are the mistakes that matter:
- **Parts mentioned but not used:** "didn't change the filter, customer had one",
  "cleaned the flame sensor instead of replacing", "didn't need the new pressure switch I brought".
- **Recommendations aren't repairs:** "recommend replacing the flue pipe next year" is a
  follow-up, not a part used.
- **Readings next to look-alike numbers:** "cap reading 43 on a 45 rated" is a reading, not
  a capacitor replacement; model numbers like MXZ-3C24NA aren't readings.
- **Indirect follow-ups:** "keep an eye on it", "said I'd get back to her with a price".
- **Genuinely ambiguous kinds** (a spring service that also fixed a leak) accept either
  reasonable answer rather than penalizing a defensible call.

## How it's scored (`ai-eval/evaluate.py`)
| Field | Metric |
|---|---|
| Kind of visit | Exact match (or an accepted alternative) |
| Follow-up needed | Exact yes/no |
| Parts | Precision and recall; a predicted part that wasn't used is a **hallucination** |
| Readings | Precision and recall on (name, value), 1% tolerance, common name aliases |
| Work done | Share of key terms mentioned |
| Follow-up note | Share of key terms mentioned (only where a follow-up is needed) |

Matching ignores case and punctuation ("R-410A" = "r410a"). Each expected part or reading
can match only one prediction. A missing or failed prediction scores as wrong, never as a crash.

## The bar (`ai-eval/thresholds.json`)
| Field | Needed | Why |
|---|---|---|
| Kind of visit | 90% | It decides "last tune-up", so it drives the reminder list |
| Follow-up needed | 90% | A missed follow-up is lost work for the owner |
| **Parts precision** | **95%** | An invented part becomes a false service record and later a wrong invoice |
| Parts recall | 90% | A missed part is easy to add while reviewing |
| **Readings precision** | **95%** | A wrong CO reading in a record is worse than none |
| Readings recall | 85% | |
| Work done key terms | 80% | |
| Follow-up note key terms | 75% | |

Precision bars sit above recall bars on purpose: the owner reviews every draft before saving,
so a gap gets filled in, but a confident wrong value may slip through.

`python3 evaluate.py ... --check` exits with status 1 below the bar, so S13 can gate on it.

## Baseline
A deliberately simple keyword extractor (`--baseline`) checks the scorer and sets a floor:

| Field | Baseline |
|---|---|
| Kind of visit | 86% |
| Follow-up needed | 97% |
| Parts precision | 71% (8 hallucinated parts) |
| Parts recall | 57% |
| Readings precision | 100% |
| Readings recall | 43% |

It fails the bar on kind, parts and readings, as it should. The traps catch it: for example
it records "the new pressure switch I brought" as a part used.

## Results (S13, October 2026)
The extractor (`OpenAiNoteExtractor`) uses OpenAI's Chat Completions with a strict JSON
schema. Settings: `OPENAI_API_KEY`, `AI_MODEL` (default `gpt-5.4-mini`).

**First run, `gpt-5.4-mini`: failed.** Parts precision 90.9% (3 invented parts), parts recall
85.7%. The misses had three patterns, each fixed with a general rule in the prompt (not by
copying test notes into it, which a test checks):
1. On installations it listed the new unit itself as a part. Rule: the unit gets its own
   equipment record; parts are the materials put in with it.
2. Shorthand lists ("nozzle filter strainer") were read too literally. Rule: a bare list
   after a routine service means those parts were replaced.
3. It flagged things the customer would handle as follow-ups. Rule: a follow-up is
   something the *business* has left to do.

One label changed after that run, and it's recorded in the data (`_relabel` on n18): "anode
rod about half gone but ok for now" now accepts either follow-up answer, because flagging it is
a defensible call. No other labels changed.

**Holdout set.** Tuning while looking at test failures makes those scores optimistic, so 12
new notes (`ai-eval/holdout.jsonl`) were written before the prompt changes and scored only
once, at the end.

| Model | Main set (3 runs) | Holdout | Invented parts |
|---|---|---|---|
| **gpt-5.4-mini** (default) | Passed every run. Parts precision 100%, recall 94–100%; kind 94–97% | **Passed.** Parts precision 100%, recall 90.9% | 0 |
| gpt-4.1-mini | Passed | **Failed:** parts precision 90.9% (a part listed twice or split) | 0 |

Takeaways: the bar caught a real difference between models; terse shorthand remains the
weakest spot (holdout recall is just above 90%); run-to-run variation is small but real, so
compare over several runs. Cost is about 700 input and 75 output tokens per note.

## Known limitation
Key-term coverage measures what a summary *mentions*, not whether it's a good summary. The
baseline copies the whole note as "work done" and scores 100% on it. Summary quality is
covered by the owner reviewing every draft before it's saved (an S13 requirement), and the
summaries can be spot-checked by hand during the trial.

## Running it
```bash
cd ai-eval
python3 -m unittest -v                                # tests for the scorer itself
python3 evaluate.py --baseline --details              # the keyword baseline, with each miss
python3 evaluate.py --predictions preds.jsonl --check # a saved set of predictions
python3 evaluate.py --endpoint http://localhost:8080/api/ai/extract --token "$TOKEN" --check  # the real AI
python3 evaluate.py --notes holdout.jsonl --endpoint ... --check                     # holdout: score, don't tune
```

Standard library only (Python 3.10+). When a prompt or model changes, rerun the set:
the numbers are the regression test.

---

# Read nameplate (S36)

A photo of a unit's data plate in; brand, model, serial number (and type, manufacture date,
a few ratings) out, for the owner to check on the equipment form. The photo is re-encoded
before it goes to the AI, which drops hidden metadata such as GPS location, and it isn't kept.

## The test set (`ai-eval/nameplates/`)
Twelve photos made by `generate.py`: data plates for ten brands (Carrier, Trane, Rheem,
Weil-McLain, Navien, Mitsubishi, Burnham, Goodman, Daikin, Lennox) with labelled model and
serial numbers, voltage and refrigerant lines, rivets and a brushed-metal finish, then
"photographed" badly on purpose: tilted up to 9°, on a concrete wall, with noise, glare, blur
and one in a dark basement. Two are unreadable on purpose (a heavily blurred plate, and a
wall with no plate at all) to check that the AI says so instead of guessing. All model and
serial numbers are made up.

## How it's scored (`evaluate_nameplates.py`)
Brand, model and serial number are compared exactly, ignoring case and spaces. **Wrong values
are counted separately and weigh more than blanks:** a blank serial is obvious to the owner,
a serial with one wrong character isn't, and it would break a warranty registration. Anything
returned for an unreadable photo counts as wrong.

| Measure | Bar |
|---|---|
| Brand, model, serial accuracy | 90% each |
| Wrong values (of those returned) | at most 5% |
| Unreadable photos reported as unreadable | 100% |

## Results (October 2026, gpt-5.4-mini)
| Measure | Result |
|---|---|
| Brand / model / serial accuracy | 100% / 100% / 100% (10 of 10 plates) |
| Wrong values | 0 |
| Unreadable photos reported as unreadable | 2 of 2 |

## Honest limitation
These plates are printed in clean digital fonts. Real plates are stamped metal, worn stickers
and dot-matrix print, often partly painted over, so this is a **floor check, not proof**. That's
why the app always asks the owner to check the model and serial against the plate before saving,
and why the plan is to add real nameplate photos from the owner's trial to this set (with any
customer details cropped out) and measure again.
