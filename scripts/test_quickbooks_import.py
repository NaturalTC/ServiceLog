import csv
import io
import os
import tempfile
import unittest
import zipfile

import quickbooks_import as qb


def xlsx(rows):
    """A minimal .xlsx with inline strings, like a spreadsheet export."""
    def col(i):
        return chr(65 + i)
    sheet_rows = "".join(
        f'<row r="{r + 1}">' + "".join(
            f'<c r="{col(c)}{r + 1}" t="inlineStr"><is><t>{v}</t></is></c>' for c, v in enumerate(row) if v != ""
        ) + "</row>"
        for r, row in enumerate(rows))
    ns = 'xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"'
    rel = 'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"'
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("xl/workbook.xml", f'<workbook {ns} {rel}><sheets><sheet name="Customers" sheetId="1" r:id="rId1"/></sheets></workbook>')
        z.writestr("xl/_rels/workbook.xml.rels", '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                   '<Relationship Id="rId1" Type="worksheet" Target="worksheets/sheet1.xml"/></Relationships>')
        z.writestr("xl/worksheets/sheet1.xml", f"<worksheet {ns}><sheetData>{sheet_rows}</sheetData></worksheet>")
    return buf.getvalue()


# QuickBooks Online style: report title rows, one-cell billing and shipping addresses
ONLINE = [
    ["Smith HVAC LLC"],
    ["Customer Contact List"],
    [],
    ["Customer full name", "Phone numbers", "Email", "Full name", "Billing address", "Shipping address"],
    ["Maria Gonzalez", "Phone: (413) 555-0101", "Maria@Example.com", "Maria Gonzalez", "10 Elm St, Springfield, MA 01103", ""],
    ["James O'Connor", "Mobile: 413.555.0102", "james@example.com", "", "PO Box 4, Springfield, MA 01101",
     "42 Chestnut St, Apt 3, Springfield, MA 01104"],
    ["No Phone Person", "", "nophone@example.com", "", "5 Oak St, Holyoke, MA 01040", ""],
    ["Address Missing", "413-555-0105", "", "", "", ""],
    ["Friday, October 23, 2026 03:15 PM GMT-04:00"],
]

# QuickBooks Desktop style: "Last, First", sub-jobs, Bill to / Ship to lines, inactive customers
DESKTOP = [
    ["Active Status", "Customer", "Company", "Main Phone", "Alt. Phone", "Main Email",
     "Bill to 1", "Bill to 2", "Bill to 3", "Bill to 4", "Ship to 1", "Ship to 2", "Ship to 3", "Notes"],
    ["Active", "Rivera, Michael", "", "", "(413) 555-0106", "", "Michael Rivera", "300 Main St", "Unit 12",
     "Chicopee, MA 01013", "", "", "", "Condo - call before arriving"],
    ["Active", "Moreau, Steven", "", "413 555 0120", "", "steven@example.com", "Steven Moreau", "8 Valley View Rd",
     "Granby MA 01033", "", "", "", "", ""],
    ["Active", "Moreau, Steven:Cottage", "", "413 555 0120", "", "", "", "", "", "", "Steven Moreau",
     "22 Lakeview Ave", "Southwick, MA 01077", ""],
    ["Inactive", "Gone, Old", "", "413-555-0199", "", "", "", "1 Old Rd", "Ludlow, MA 01056", "", "", "", "", ""],
    ["Active", "Acme Property Mgmt, Inc", "Acme Property Mgmt, Inc", "413-555-0150", "", "", "", "77 Front St",
     "Holyoke, Massachusetts 01040", "", "", "", "", ""],
]


class AddressTest(unittest.TestCase):
    def test_one_line_with_unit(self):
        self.assertEqual(qb.parse_address("250 State St, Apt 5B, Springfield, MA 01103"),
                         {"street": "250 State St", "unit": "Apt 5B", "city": "Springfield", "state": "MA", "zip": "01103"})

    def test_multi_line_with_name_first_and_country(self):
        a = qb.parse_address("Linda Nguyen\n7 Laurel Ave\nWest Springfield MA 01089-1234\nUSA", skip=("Linda Nguyen",))
        self.assertEqual((a["street"], a["city"], a["state"], a["zip"]), ("7 Laurel Ave", "West Springfield", "MA", "01089"))

    def test_inline_unit_and_full_state_name(self):
        a = qb.parse_address("300 Main St Unit 12, Chicopee, Massachusetts 01013")
        self.assertEqual((a["street"], a["unit"], a["state"]), ("300 Main St", "Unit 12", "MA"))

    def test_incomplete(self):
        self.assertIsNone(qb.parse_address("10 Elm St"))
        self.assertIsNone(qb.parse_address("Springfield, MA 01103"))
        self.assertIsNone(qb.parse_address("John Smith, Springfield, MA 01103"))


class CleaningTest(unittest.TestCase):
    def test_names(self):
        self.assertEqual(qb.clean_name("Smith, John"), "John Smith")
        self.assertEqual(qb.clean_name("Smith, John:Basement"), "John Smith")
        self.assertEqual(qb.clean_name("", "Ann", "Lee"), "Ann Lee")
        self.assertEqual(qb.clean_name("Acme Property Mgmt, Inc"), "Acme Property Mgmt, Inc")

    def test_phones(self):
        self.assertEqual(qb.normalize_phone("Phone: (413) 555-0101 Mobile: 413-555-0199"), "+14135550101")
        self.assertEqual(qb.normalize_phone("+1 413.555.0102"), "+14135550102")
        self.assertIsNone(qb.normalize_phone("555-0101"))
        self.assertIsNone(qb.normalize_phone("(013) 555-0101"))


class PlanTest(unittest.TestCase):
    def test_quickbooks_online_xlsx(self):
        rows = qb.read_xlsx(xlsx(ONLINE))
        result = qb.plan(rows)
        self.assertEqual([c.name for c in result.create], ["Maria Gonzalez", "James O'Connor"])
        maria, james = result.create
        self.assertEqual((maria.phone, maria.email), ("+14135550101", "maria@example.com"))
        # The service address is the shipping one, not the PO box it's billed to
        self.assertEqual(james.addresses[0]["street"], "42 Chestnut St")
        self.assertEqual(james.addresses[0]["unit"], "Apt 3")
        self.assertEqual({c.name: c.problems for c in result.fix},
                         {"No Phone Person": ["no phone"], "Address Missing": ["no address"]})

    def test_quickbooks_desktop_csv(self):
        result = qb.plan(DESKTOP)
        names = {c.name: c for c in result.create}
        self.assertEqual(set(names), {"Michael Rivera", "Steven Moreau", "Acme Property Mgmt, Inc"})
        self.assertEqual(names["Michael Rivera"].phone, "+14135550106")  # from Alt. Phone
        self.assertEqual(names["Michael Rivera"].addresses[0]["unit"], "Unit 12")
        self.assertEqual(names["Michael Rivera"].notes, "Condo - call before arriving")
        # The sub-job became a second property of the same customer
        self.assertEqual([a["city"] for a in names["Steven Moreau"].addresses], ["Granby", "Southwick"])
        self.assertEqual(result.merged, 1)
        self.assertEqual(result.inactive, 1)
        self.assertEqual(names["Acme Property Mgmt, Inc"].addresses[0]["state"], "MA")

    def test_skips_customers_already_in_servicelog(self):
        existing = [
            {"name": "Someone Else", "phone": "+14135550101", "email": None, "properties": []},
            {"name": "James O'Connor", "phone": "+14135559999", "email": "other@example.com",
             "properties": [{"street": "42 Chestnut St", "unit": "Apt 3", "zip": "01104"}]},
        ]
        result = qb.plan(qb.read_xlsx(xlsx(ONLINE)), existing)
        self.assertEqual(result.create, [])
        self.assertEqual(sorted(m for _, m in result.duplicates), ["James O'Connor", "Someone Else"])

    def test_fix_file_round_trip(self):
        result = qb.plan(qb.read_xlsx(xlsx(ONLINE)))
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "fix.csv")
            qb.write_fixes(path, result.fix)
            with open(path, newline="") as f:
                rows = list(csv.reader(f))
            # The owner fills in what's missing...
            rows[1][1] = "413-555-0104"
            rows[2][3:8] = ["5 Birch Rd", "", "Agawam", "", "01001"]
            with open(path, "w", newline="") as f:
                csv.writer(f).writerows(rows)
            # ...and the same script takes the file back (the missing state comes from the ZIP)
            again = qb.plan(qb.read_rows(path))
        self.assertEqual([c.name for c in again.create], ["No Phone Person", "Address Missing"])
        self.assertEqual(again.create[1].addresses[0]["state"], "MA")
        body = qb.request_body(again.create[1])
        self.assertEqual(body["properties"][0]["street"], "5 Birch Rd")

    def test_needs_a_header(self):
        with self.assertRaises(SystemExit):
            qb.plan([["hello"], ["world"]])


if __name__ == "__main__":
    unittest.main()
