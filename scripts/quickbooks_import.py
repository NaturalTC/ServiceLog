#!/usr/bin/env python3
"""Moves customers from a QuickBooks export into ServiceLog.

Works with the customer list exported from QuickBooks Online (Customers > Export to Excel,
or the Customer Contact List report) and QuickBooks Desktop (Customer Center > Excel >
Export Customer List), as .xlsx or .csv. Columns are found by their names, so extra
columns, a different order, or report title rows above the header don't matter.

It only reads the file and talks to ServiceLog's API: nothing in QuickBooks changes.

    # 1. See what would happen (nothing is created)
    python3 scripts/quickbooks_import.py Customers.xlsx --url https://... --email owner@...

    # 2. Create them
    python3 scripts/quickbooks_import.py Customers.xlsx --url https://... --email owner@... --apply

The password is asked for (or read from the SERVICELOG_PASSWORD environment variable),
never passed on the command line.

Customers already in ServiceLog (same phone, same email, or same name at the same street)
are skipped, so running it twice is safe. Customers ServiceLog can't take as they are
(no phone, no complete service address) go to a "needs fixing" CSV: fill in the blanks and
run the script on that file. Uses only the Python standard library.
"""
import argparse
import csv
import getpass
import io
import json
import os
import re
import sys
import urllib.error
import urllib.request
import zipfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field

# --- Reading the file ---------------------------------------------------------------

XLSX_NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
REL_NS = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"


def read_rows(path):
    """All rows of the file as lists of strings (first sheet of a workbook)."""
    lower = path.lower()
    if lower.endswith(".xlsx"):
        with open(path, "rb") as f:
            return read_xlsx(f.read())
    if lower.endswith(".xls"):
        raise SystemExit("That's an old-style .xls file. Open it in Excel or Numbers and save it as .xlsx or .csv.")
    with open(path, encoding="utf-8-sig", newline="") as f:
        text = f.read()
    try:
        dialect = csv.Sniffer().sniff(text[:4096], delimiters=",;\t")
    except csv.Error:
        dialect = csv.excel
    return [row for row in csv.reader(io.StringIO(text), dialect)]


def read_xlsx(data):
    """The first worksheet of an .xlsx file, without third-party libraries."""
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        shared = []
        if "xl/sharedStrings.xml" in z.namelist():
            for si in ET.fromstring(z.read("xl/sharedStrings.xml")).findall("m:si", XLSX_NS):
                shared.append("".join(t.text or "" for t in si.iter(f"{{{XLSX_NS['m']}}}t")))
        sheet = first_sheet_path(z)
        rows = []
        for row in ET.fromstring(z.read(sheet)).iter(f"{{{XLSX_NS['m']}}}row"):
            values = {}
            for c in row.findall("m:c", XLSX_NS):
                col = column_index(c.get("r", ""))
                kind = c.get("t")
                if kind == "inlineStr":
                    text = "".join(t.text or "" for t in c.iter(f"{{{XLSX_NS['m']}}}t"))
                else:
                    v = c.find("m:v", XLSX_NS)
                    text = v.text if v is not None and v.text else ""
                    if kind == "s" and text:
                        text = shared[int(text)]
                    elif kind is None and re.fullmatch(r"-?\d+\.0", text):
                        text = text[:-2]  # whole numbers stored as floats, e.g. ZIP codes
                values[col if col is not None else len(values)] = text
            if values:
                rows.append([values.get(i, "") for i in range(max(values) + 1)])
        return rows


def first_sheet_path(z):
    workbook = ET.fromstring(z.read("xl/workbook.xml"))
    sheet = workbook.find("m:sheets/m:sheet", XLSX_NS)
    rels = ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))
    for rel in rels:
        if sheet is not None and rel.get("Id") == sheet.get(REL_NS):
            target = rel.get("Target").lstrip("/")
            return target if target.startswith("xl/") else "xl/" + target
    return "xl/worksheets/sheet1.xml"


def column_index(ref):
    letters = re.match(r"[A-Z]+", ref or "")
    if not letters:
        return None
    n = 0
    for ch in letters.group(0):
        n = n * 26 + ord(ch) - 64
    return n - 1


# --- Finding the columns --------------------------------------------------------------

# Header names seen in QuickBooks Online and Desktop exports (and ServiceLog's own fix-up
# file), lower-cased with punctuation removed. First match wins within each field.
FIELDS = {
    "name": ["customer", "customer full name", "display name", "customer name", "name", "full name"],
    "company": ["company", "company name"],
    "first": ["first name", "first"],
    "last": ["last name", "last"],
    "phone": ["phone", "main phone", "phone numbers", "phone number", "mobile", "mobile phone", "home phone",
              "work phone", "alt phone", "cell", "cell phone"],
    "email": ["email", "main email", "email address", "e mail"],
    "notes": ["notes", "note", "customer notes"],
    "status": ["active status", "status", "active"],
    # One-cell addresses
    "ship": ["shipping address", "ship to address", "ship to", "service address"],
    "bill": ["billing address", "bill to address", "bill to", "address", "full address"],
    # Split addresses (ServiceLog's fix-up file and some QuickBooks exports)
    "street": ["street", "street address", "billing street", "billing address line 1", "bill street1",
               "bill to street1", "address line 1", "address 1"],
    "street2": ["billing address line 2", "bill street2", "bill to street2", "address line 2", "address 2"],
    "unit": ["unit", "apt", "apartment", "suite"],
    "city": ["city", "billing city", "bill city", "bill to city"],
    "state": ["state", "billing state", "bill state", "bill to state", "province", "state province"],
    "zip": ["zip", "zip code", "postal code", "billing zip", "billing postal code", "bill zip", "bill to zip",
            "postcode"],
}
# QuickBooks Desktop splits addresses over "Bill to 1" ... "Bill to 5" (and "Ship to 1" ...)
NUMBERED = {"bill": re.compile(r"^bill(?: to)? ?([1-5])$"), "ship": re.compile(r"^ship(?: to)? ?([1-5])$")}


def header_key(text):
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", str(text).lower())).strip()


def map_columns(header):
    """{field: [column indexes]} for a header row; phone keeps every phone-like column."""
    keys = [header_key(h) for h in header]
    found = {}
    for fieldname, names in FIELDS.items():
        for name in names:
            matches = [i for i, k in enumerate(keys) if k == name]
            if matches:
                if fieldname == "phone":
                    found.setdefault("phone", []).extend(i for i in matches if i not in found.get("phone", []))
                    continue
                found[fieldname] = matches[:1]
                break
    for fieldname, pattern in NUMBERED.items():
        lines = sorted((int(m.group(1)), i) for i, k in enumerate(keys) if (m := pattern.match(k)))
        if lines:
            found[fieldname + "_lines"] = [i for _, i in lines]
    return found


def find_header(rows):
    """QuickBooks reports put a title and date above the header: pick the row that looks most like one."""
    best, best_score = None, 0
    for i, row in enumerate(rows[:15]):
        cols = map_columns(row)
        score = len(cols) + (2 if "name" in cols else 0)
        if score > best_score:
            best, best_score = i, score
    if best is None or best_score < 3:
        raise SystemExit("Couldn't find a header row with customer columns (Customer/Name, Phone, Address...).")
    return best


# --- Cleaning up one customer ------------------------------------------------------------

STATES = set("AL AK AZ AR CA CO CT DE DC FL GA HI ID IL IN IA KS KY LA ME MD MA MI MN MS MO MT NE NV NH NJ NM "
             "NY NC ND OH OK OR PA RI SC SD TN TX UT VT VA WA WV WI WY PR".split())
STATE_NAMES = {"massachusetts": "MA", "connecticut": "CT", "new york": "NY", "vermont": "VT",
               "new hampshire": "NH", "rhode island": "RI", "maine": "ME"}
UNIT_RE = re.compile(r"^(?:apt\.?|apartment|unit|ste\.?|suite|#|fl\.?|floor|bldg\.?|building|rm\.?|room)\s*#?\s*\S+$",
                     re.IGNORECASE)
INLINE_UNIT_RE = re.compile(r"^(.*?\S)\s*,?\s+((?:apt\.?|apartment|unit|ste\.?|suite|#)\s*#?\s*[\w-]+)$", re.IGNORECASE)
# "Springfield, MA 01103", "West Springfield MA 01089-1234", "Holyoke, Massachusetts 01040", "Agawam 01001"
LAST_LINE_RE = re.compile(r"^(?P<city>[A-Za-z .'-]+?)(?:\s*,?\s+(?P<state>[A-Za-z]{2}|" + "|".join(STATE_NAMES)
                          + r"))?\s*,?\s+(?P<zip>\d{5})(?:-\d{4})?$", re.IGNORECASE)
PHONE_RE = re.compile(r"(?:\+?1[\s.-]*)?\(?\d{3}\)?[\s.-]*\d{3}[\s.-]*\d{4}")


def normalize_phone(text):
    """+1XXXXXXXXXX for the first valid U.S. number in the text (same rule as the app), or None."""
    for match in PHONE_RE.finditer(text or ""):
        digits = re.sub(r"\D", "", match.group(0))
        if len(digits) == 11 and digits.startswith("1"):
            digits = digits[1:]
        if len(digits) == 10 and digits[0] >= "2" and digits[3] >= "2":
            return "+1" + digits
    return None


def clean_name(raw, first="", last="", company=""):
    """'Smith, John' -> 'John Smith'; 'Smith, John:Basement job' -> 'John Smith' (QuickBooks sub-jobs)."""
    name = (raw or "").split(":")[0].strip()
    if not name:
        name = " ".join(p for p in (first.strip(), last.strip()) if p) or company.strip()
    m = re.fullmatch(r"([^,]+),\s*([^,]+)", name)
    if m and not re.search(r"\b(inc|llc|co|corp|ltd|trust|associates)\b\.?", name, re.IGNORECASE):
        name = f"{m.group(2).strip()} {m.group(1).strip()}"
    return re.sub(r"\s+", " ", name)


def zip_state(zip_code):
    """Massachusetts ZIPs start 010-027: fills a missing state for a western-Mass shop."""
    return "MA" if zip_code and "01000" <= zip_code[:5] <= "02799" else None


def parse_address(text, skip=()):
    """'250 State St, Apt 5B, Springfield, MA 01103' (one line or several) -> address dict, or None."""
    lines = [p.strip() for p in re.split(r"[\n\r]+|,", text or "") if p.strip()]
    lines = [p for p in lines if p.lower() not in {"usa", "us", "united states", "united states of america"}]
    skip = {s.lower() for s in skip if s}
    # The last part (or last two or three parts) hold city, state and ZIP; take the first
    # reading that leaves a real street in front of it
    for take in (1, 2, 3):
        if len(lines) <= take:
            break
        m = LAST_LINE_RE.match(" ".join(lines[-take:]))
        if not m or m.group("city").strip().upper() in STATES:
            continue
        address = street_part([p for p in lines[:-take] if p.lower() not in skip])
        if address:
            given = (m.group("state") or "").strip()
            city = m.group("city").strip()
            address.update(city=city.title() if city.isupper() else city, zip=m.group("zip"),
                           state=STATE_NAMES.get(given.lower(), given.upper()) or zip_state(m.group("zip")) or "")
            return address
    return None


def street_part(rest):
    """The street (and unit) from the lines before the city; earlier lines are a name or "c/o"."""
    if not rest:
        return None
    unit = None
    if len(rest) > 1 and UNIT_RE.match(rest[-1]):
        unit = rest.pop()
    street = rest[-1]
    if not unit:
        inline = INLINE_UNIT_RE.match(street)
        if inline and re.search(r"\d", inline.group(1)):
            street, unit = inline.group(1), inline.group(2)
    if not re.search(r"\d", street):
        return None  # no house number: not a street we can drive to
    return {"street": street, "unit": unit}


@dataclass
class Customer:
    line: int
    name: str
    phone: str | None
    email: str | None
    notes: str | None
    addresses: list = field(default_factory=list)
    problems: list = field(default_factory=list)
    raw_phone: str = ""
    raw_address: str = ""


def cell(row, cols, fieldname):
    idx = cols.get(fieldname)
    return row[idx[0]].strip() if idx and idx[0] < len(row) else ""


def to_customer(row, cols, line):
    name = clean_name(cell(row, cols, "name"), cell(row, cols, "first"), cell(row, cols, "last"),
                      cell(row, cols, "company"))
    company = cell(row, cols, "company")
    phones = [row[i] for i in cols.get("phone", []) if i < len(row) and row[i].strip()]
    phone = next((p for p in (normalize_phone(x) for x in phones) if p), None)
    email = cell(row, cols, "email").split(";")[0].split(",")[0].strip().lower() or None
    if email and not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email):
        email = None
    notes = [cell(row, cols, "notes")]
    if company and company.lower() != name.lower():
        notes.insert(0, f"Company: {company}")
    notes = "\n".join(n for n in notes if n)[:2000] or None

    address, raw = None, ""
    if cols.get("street") or cols.get("city"):
        street = ", ".join(x for x in (cell(row, cols, "street"), cell(row, cols, "street2")) if x)
        raw = ", ".join(x for x in (street, cell(row, cols, "unit"), cell(row, cols, "city"),
                                    f"{cell(row, cols, 'state')} {cell(row, cols, 'zip')}".strip()) if x)
        address = parse_address(raw)
        if address and cell(row, cols, "unit"):
            address["unit"] = cell(row, cols, "unit")
    # The service address is where the equipment is: Ship-to when there is one, else Bill-to
    for kind in ("ship", "bill"):
        if address:
            break
        parts = [cell(row, cols, kind)] + [row[i] for i in cols.get(kind + "_lines", []) if i < len(row)]
        raw = "\n".join(p for p in parts if p and p.strip())
        address = parse_address(raw, skip=(name, company, cell(row, cols, "name")))
    if address and len(address["state"]) != 2:
        address["state"] = zip_state(address["zip"]) or address["state"]

    c = Customer(line, name[:120], phone, email, notes, raw_phone=" / ".join(phones), raw_address=raw)
    if not name:
        c.problems.append("no name")
    if not phone:
        c.problems.append("no valid phone" if phones else "no phone")
    if not address:
        c.problems.append("address incomplete" if raw else "no address")
    elif address["state"] not in STATES:
        c.problems.append("state unclear")
    else:
        c.addresses.append(address)
    return c


def inactive(row, cols):
    status = cell(row, cols, "status").lower()
    return status in {"inactive", "no", "false", "deleted"}


# --- Deciding what to do ------------------------------------------------------------------

@dataclass
class Plan:
    create: list = field(default_factory=list)
    duplicates: list = field(default_factory=list)  # (customer, matched name)
    fix: list = field(default_factory=list)
    inactive: int = 0
    merged: int = 0


def street_key(a):
    return re.sub(r"[^a-z0-9]", "", f"{a['street']}{a.get('unit') or ''}{a['zip']}".lower())


def plan(rows, existing=(), include_inactive=False):
    header_at = find_header(rows)
    cols = map_columns(rows[header_at])
    result = Plan()
    by_phone, by_email, by_name_street = {}, {}, {}
    for e in existing:
        if e.get("phone"):
            by_phone[e["phone"]] = e["name"]
        if e.get("email"):
            by_email[e["email"].lower()] = e["name"]
        for p in e.get("properties", []):
            by_name_street[(e["name"].lower(), street_key(p))] = e["name"]

    seen = {}  # phone -> Customer in this file, to merge sub-jobs and extra addresses
    for n, row in enumerate(rows[header_at + 1:], start=header_at + 2):
        if not any(str(c).strip() for c in row):
            continue
        if not include_inactive and inactive(row, cols):
            result.inactive += 1
            continue
        first_cell = (row[0] if row else "").strip().lower()
        if first_cell.startswith("total") or re.match(r"^[a-z]+day, ", first_cell):
            continue  # report footers
        c = to_customer(row, cols, n)
        if c.phone and c.phone in seen:
            # Same person again (a QuickBooks sub-job, or a second property): one customer, more addresses
            first = seen[c.phone]
            for a in c.addresses:
                if all(street_key(a) != street_key(b) for b in first.addresses):
                    first.addresses.append(a)
                    if "no address" in first.problems or "address incomplete" in first.problems:
                        first.problems = [p for p in first.problems if "address" not in p]
            result.merged += 1
            continue
        match = (by_phone.get(c.phone) or (c.email and by_email.get(c.email))
                 or next((by_name_street[(c.name.lower(), street_key(a))] for a in c.addresses
                          if (c.name.lower(), street_key(a)) in by_name_street), None))
        if match:
            result.duplicates.append((c, match))
            continue
        if c.phone:
            seen[c.phone] = c
        (result.fix if c.problems else result.create).append(c)
    # A merge can complete someone who was missing an address
    for c in list(result.fix):
        if not c.problems:
            result.fix.remove(c)
            result.create.append(c)
    return result


def request_body(c):
    return {"name": c.name, "phone": c.phone, "email": c.email, "notes": c.notes,
            "properties": [{"street": a["street"][:200], "unit": (a.get("unit") or None) and a["unit"][:50],
                            "city": a["city"][:100], "state": a["state"], "zip": a["zip"], "notes": None}
                           for a in c.addresses]}


FIX_HEADER = ["Name", "Phone", "Email", "Street", "Unit", "City", "State", "Zip", "Notes", "Problem",
              "QuickBooks phone", "QuickBooks address"]


def write_fixes(path, customers):
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(FIX_HEADER)
        for c in customers:
            a = c.addresses[0] if c.addresses else {}
            w.writerow([c.name, c.phone or "", c.email or "", a.get("street", ""), a.get("unit") or "",
                        a.get("city", ""), a.get("state", ""), a.get("zip", ""), c.notes or "",
                        "; ".join(c.problems), c.raw_phone, c.raw_address.replace("\n", ", ")])


# --- Talking to ServiceLog ---------------------------------------------------------------

class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.token = None

    def call(self, method, path, body=None):
        data = json.dumps(body).encode() if body is not None else None
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        req = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req) as res:
                payload = res.read()
                return res.status, json.loads(payload) if payload else None
        except urllib.error.HTTPError as e:
            payload = e.read()
            try:
                return e.code, json.loads(payload) if payload else None
            except ValueError:
                return e.code, None


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("file", help="QuickBooks customer export (.xlsx or .csv), or a filled-in fix-up file")
    parser.add_argument("--url", default="http://localhost:8080", help="ServiceLog address")
    parser.add_argument("--email", help="ServiceLog owner login (needed to check for duplicates and to import)")
    parser.add_argument("--apply", action="store_true", help="create the customers (without it, only a preview)")
    parser.add_argument("--include-inactive", action="store_true", help="also import customers marked inactive")
    parser.add_argument("--fixes-out", default="quickbooks-needs-fixing.csv",
                        help="where to write customers that need details filled in")
    args = parser.parse_args(argv)

    rows = read_rows(args.file)
    existing = []
    api = None
    if args.email:
        password = os.environ.get("SERVICELOG_PASSWORD") or getpass.getpass("ServiceLog password: ")
        api = Api(args.url)
        status, body = api.call("POST", "/api/auth/login", {"email": args.email, "password": password})
        if status != 200:
            raise SystemExit(f"Couldn't log in to {args.url} ({status}).")
        api.token = body["token"]
        status, existing = api.call("GET", "/api/customers")
        if status != 200:
            raise SystemExit(f"Couldn't read existing customers ({status}).")
    elif args.apply:
        raise SystemExit("--apply needs --email to log in to ServiceLog.")
    else:
        print("(No --email: not checking for customers already in ServiceLog.)\n")

    result = plan(rows, existing, args.include_inactive)
    addresses = sum(len(c.addresses) for c in result.create)
    print(f"Ready to import:      {len(result.create)} customers ({addresses} service addresses)")
    print(f"Already in ServiceLog: {len(result.duplicates)} (skipped)")
    if result.merged:
        print(f"Merged:                {result.merged} rows were the same customer (sub-jobs or extra addresses)")
    if result.inactive:
        print(f"Inactive in QuickBooks: {result.inactive} (skipped; --include-inactive to bring them over)")
    print(f"Need details:          {len(result.fix)}")
    for c in result.fix[:10]:
        print(f"  line {c.line}: {c.name or '(no name)'}: {', '.join(c.problems)}")
    if len(result.fix) > 10:
        print(f"  ... and {len(result.fix) - 10} more")
    if result.fix:
        write_fixes(args.fixes_out, result.fix)
        print(f"\nWrote {args.fixes_out}: fill in the blanks, then run this script on that file.")

    if not args.apply:
        print("\nPreview only. Add --apply to create them.")
        return 0
    created, failed = 0, []
    for c in result.create:
        status, body = api.call("POST", "/api/customers", request_body(c))
        if status in (200, 201):
            created += 1
        else:
            failed.append((c, (body or {}).get("message") or (body or {}).get("error") or status))
    print(f"\nCreated {created} customers.")
    for c, why in failed:
        print(f"  Not created, line {c.line}: {c.name}: {why}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
