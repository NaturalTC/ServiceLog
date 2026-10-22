#!/usr/bin/env python3
"""Measures "Read nameplate" (S36) on the synthetic plates in plates/ against truth.jsonl.

Usage (backend running with OPENAI_API_KEY; standard library only):
    python3 evaluate_nameplates.py --endpoint http://localhost:8080 --token-file token.txt [--check]

For each plate it scores brand, model and serial number (exact, ignoring case and spaces),
and separately counts wrong values: a model or serial returned that isn't what's printed,
including anything returned for a plate that can't be read. Wrong values are worse than
blanks, since a blank is obvious to the owner and a wrong serial isn't.
"""
import argparse
import json
import sys
import urllib.request
import uuid
from pathlib import Path

HERE = Path(__file__).parent


def norm(value):
    return None if value is None else "".join(str(value).split()).upper()


def read_plate(endpoint, token, path):
    boundary = uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"{path.name}\"\r\n"
            f"Content-Type: image/jpeg\r\n\r\n").encode() + path.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    request = urllib.request.Request(f"{endpoint}/api/assistant/nameplate", data=body, method="POST", headers={
        "Content-Type": f"multipart/form-data; boundary={boundary}", "Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.load(response)


def score(truth, results):
    """truth and results: lists of dicts in the same order. Returns the metrics."""
    readable = [(t, r) for t, r in zip(truth, results) if t["readable"]]
    unreadable = [(t, r) for t, r in zip(truth, results) if not t["readable"]]
    m = {}
    for field, key in (("brand", "brand"), ("model", "model"), ("serial", "serialNumber")):
        m[f"{field}_accuracy"] = sum(norm(t[field]) == norm(r.get(key)) for t, r in readable) / max(len(readable), 1)
    returned = wrong = 0
    for t, r in zip(truth, results):
        for field, key in (("model", "model"), ("serial", "serialNumber")):
            if r.get(key) is not None:
                returned += 1
                if norm(r.get(key)) != norm(t[field]):
                    wrong += 1
    m["wrong_values"] = wrong
    m["wrong_value_rate"] = wrong / max(returned, 1)
    m["unreadable_flagged"] = sum(not r.get("readable") or (r.get("model") is None and r.get("serialNumber") is None)
                                  for _, r in unreadable) / max(len(unreadable), 1)
    return m


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--endpoint", required=True)
    parser.add_argument("--token-file", required=True)
    parser.add_argument("--check", action="store_true", help="exit 1 if below thresholds.json")
    args = parser.parse_args()
    token = Path(args.token_file).read_text().strip()
    truth = [json.loads(line) for line in (HERE / "truth.jsonl").read_text().splitlines() if line.strip()]
    results = []
    for t in truth:
        r = read_plate(args.endpoint, token, HERE / "plates" / t["file"])
        results.append(r)
        ok = all(norm(t[f]) == norm(r.get(k)) for f, k in (("brand", "brand"), ("model", "model"),
                                                            ("serial", "serialNumber"))) if t["readable"] else None
        print(f"{t['file']}: {r.get('brand')} | {r.get('model')} | {r.get('serialNumber')} | "
              f"{r.get('confidence')} {'OK' if ok else ('MISS' if ok is False else 'unreadable: ' + str(not r.get('readable')))}")
    metrics = score(truth, results)
    print(json.dumps(metrics, indent=2))
    if args.check:
        bar = json.loads((HERE / "thresholds.json").read_text())
        failed = [k for k, v in bar.items() if (metrics[k] > v if k.startswith("wrong") else metrics[k] < v)]
        print("FAILED: " + ", ".join(failed) if failed else "PASSED")
        sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
