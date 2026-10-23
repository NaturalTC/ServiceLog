"""Tests for the assistant evaluation scorer (assistant/evaluate_assistant.py)."""
import datetime as dt
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent / "assistant"))
import evaluate_assistant as ev  # noqa: E402


class CheckTest(unittest.TestCase):

    def test_text_and_lookup_checks(self):
        reply = {"reply": "Today: David Kowalski at 8.", "lookups": ["list_jobs"], "proposals": []}
        self.assertEqual(ev.check({"lookups_any": ["list_jobs"], "contains_all": ["kowalski"]}, reply), [])
        failed = ev.check({"lookups_any": ["tune_ups_due"], "contains_any": ["Popescu"],
                           "not_contains": ["David"]}, reply)
        self.assertEqual(len(failed), 3)

    def test_curly_apostrophes_count_as_straight_ones(self):
        reply = {"reply": "I couldn\u2019t find that customer. Don\u2019t jumper it.", "proposals": []}
        self.assertEqual(ev.check({"contains_any": ["couldn't find"], "contains_all": ["don't"]}, reply), [])

    def test_proposal_matching_uses_exact_payload_and_substrings(self):
        reply = {"reply": "", "proposals": [{"type": "CUSTOMER", "details": [{"label": "Name", "value": "Paul Novak"}],
                                             "payload": {"name": "Paul Novak", "phone": "413-555-0190",
                                                         "properties": [{"city": "Ludlow"}]}}]}
        good = {"proposal": {"type": "CUSTOMER", "payload": {"name": "Paul Novak"},
                             "payload_contains": {"phone": "555-0190", "properties.0.city": "ludlow"},
                             "details_contain": ["Novak"]}}
        self.assertEqual(ev.check(good, reply), [])
        wrong = {"proposal": {"type": "CUSTOMER", "payload": {"name": "Paul Novack"}}}
        self.assertEqual(len(ev.check(wrong, reply)), 1)
        self.assertEqual(len(ev.check({"no_proposal": True}, reply)), 1)

    def test_dates_are_worked_out_for_the_scenarios(self):
        values = ev.dates(dt.date(2026, 10, 22))  # a Thursday
        self.assertEqual(values["tomorrow"], "2026-10-23")
        self.assertEqual(values["next_tuesday"], "2026-10-27")
        self.assertEqual(values["next_tuesday_text"], "Tuesday, October 27")
        self.assertEqual(ev.dates(dt.date(2026, 10, 27))["next_tuesday"], "2026-11-03")  # a Tuesday: the next one
        self.assertEqual(ev.fill({"date": "{tomorrow}"}, values), {"date": "2026-10-23"})


class LeakTest(unittest.TestCase):

    def test_finds_names_phones_emails_and_streets_but_not_placeholders(self):
        details = ev.private_details([{"name": "David Kowalski", "phone": "+14135550108",
                                       "email": "david@example.com", "properties": [{"street": "64 Pleasant St"}]}])
        self.assertIn("555-0108", details)
        clean = ['{"content": "Customer_8 at Street_2, Phone_1"}']
        self.assertEqual(ev.leaks(clean, details), [])
        dirty = ['{"content": "call 413-555-0108"}', '{"content": "David Kowalski, 64 pleasant st"}']
        self.assertEqual(sorted(d for d, _ in ev.leaks(dirty, details)),
                         ["555-0108", "64 Pleasant St", "David Kowalski"])


if __name__ == "__main__":
    unittest.main()
