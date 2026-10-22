#!/usr/bin/env python3
"""Fills an EMPTY ServiceLog database with realistic, entirely fake demo data.

Twenty customers in western Massachusetts towns, their HVAC equipment, a few years of
service history, and today's schedule. Everything goes through the app's own API, so it
passes the same validation as real data. Names, streets, 555-01xx phone numbers and
example.com emails are made up. Uses only the Python standard library.

Usage (backend running, owner account able to log in):
    python3 scripts/seed-demo-data.py --url http://localhost:8080 --email you@example.com --password '...'

For a brand-new local database whose owner hasn't verified their email yet, add
--verify-with-mailpit to click the verification link from Mailpit (development only).

Dates are relative to the server's "today", and the random choices use a fixed seed, so
the data looks the same every time it's generated.
"""
import argparse
import datetime as dt
import json
import random
import re
import sys
import urllib.error
import urllib.request

CUSTOMERS = [
    ("Maria Gonzalez", "0101", "10 Elm St", None, "Springfield", "01103", "Oil burner in basement"),
    ("James O'Connor", "0102", "42 Chestnut St", "Apt 3", "Springfield", "01104", "Mini-split, 2 zones"),
    ("Linda Nguyen", "0103", "7 Laurel Ave", None, "West Springfield", "01089", None),
    ("Robert Smith", "0104", "155 Oak Ridge Rd", None, "Westfield", "01085", "Dog in yard"),
    ("Patricia Brown", "0105", "9 Mill Pond Ln", None, "Holyoke", "01040", None),
    ("Michael Rivera", "0106", "300 Main St", "Unit 12", "Chicopee", "01013", "Condo - call before arriving"),
    ("Jennifer Lee", "0107", "18 Birch Hill Dr", None, "Agawam", "01001", None),
    ("David Kowalski", "0108", "64 Pleasant St", None, "Easthampton", "01027", "Boiler is 20+ years old"),
    ("Susan Murphy", "0109", "5 Orchard Way", None, "Southwick", "01077", None),
    ("Carlos Santos", "0110", "221 Northampton St", None, "Holyoke", "01040", "Gate code 1234"),
    ("Karen Fitzgerald", "0111", "77 Prospect Hill Rd", None, "Longmeadow", "01106", None),
    ("Thomas Clark", "0112", "12 Sunset Ter", None, "Ludlow", "01056", "Heat pump installed 2023"),
    ("Angela Martinez", "0113", "430 Boston Rd", None, "Wilbraham", "01095", None),
    ("Kevin Walsh", "0114", "3 Maple Ct", None, "Northampton", "01060", "Prefers texts"),
    ("Nancy Dubois", "0115", "88 Pine Ave", None, "East Longmeadow", "01028", None),
    ("Brian Sullivan", "0116", "19 Riverside Dr", None, "Westfield", "01085", "Rental owner"),
    ("Elena Popescu", "0117", "250 State St", "Apt 5B", "Springfield", "01103", None),
    ("Daniel Okafor", "0118", "61 Hampden St", None, "Chicopee", "01020", "Oil tank in garage"),
    ("Rachel Goldberg", "0119", "14 Cedar Ln", None, "Hadley", "01035", None),
    ("Steven Moreau", "0120", "8 Valley View Rd", None, "Granby", "01033", "Two properties"),
]
SECOND_PROPERTY = {"Steven Moreau": ("22 Lakeview Ave", "Southwick", "01077", "Summer cottage")}


def unit(t, brand=None, model=None, serial=None, date=None, notes=None):
    return {"type": t, "brand": brand, "model": model, "serialNumber": serial, "installDate": date, "notes": notes}


EQUIPMENT = [
    ("Maria Gonzalez", 0, [unit("OIL_BURNER", "Beckett", "AFG", "DEMO-OB-0001", "2014-10-01"),
                           unit("WATER_HEATER", "Bradford White", "RE340S6", "DEMO-WH-0002", "2019-03-12")]),
    ("James O'Connor", 0, [unit("MINI_SPLIT_OUTDOOR", "Mitsubishi", "MXZ-2C20NAHZ", "DEMO-MS-1001", "2022-05-20"),
                           unit("MINI_SPLIT_INDOOR", "Mitsubishi", "MSZ-GL09NA", "DEMO-MS-1002", "2022-05-20", "Living room"),
                           unit("MINI_SPLIT_INDOOR", "Mitsubishi", "MSZ-GL12NA", "DEMO-MS-1003", "2022-05-20", "Primary bedroom")]),
    ("Linda Nguyen", 0, [unit("FURNACE", "Carrier", "59SC5A060E17", "DEMO-FU-2001", "2016-11-02", "Filter 16x25x1"),
                         unit("CENTRAL_AC", "Carrier", "24ACC636A003", "DEMO-AC-2002", "2016-11-02")]),
    ("Robert Smith", 0, [unit("BOILER", "Burnham", "ES2-4", "DEMO-BO-3001", "2011-10-15"),
                         unit("OIL_BURNER", "Beckett", "AFG", "DEMO-OB-3002", "2011-10-15")]),
    ("Patricia Brown", 0, [unit("HEAT_PUMP", "Fujitsu", "AOU24RLXFZH", "DEMO-HP-4001", "2021-08-30")]),
    ("Michael Rivera", 0, [unit("MINI_SPLIT_OUTDOOR", "Daikin", "RX12AXVJU", "DEMO-MS-5001", "2020-06-11"),
                           unit("MINI_SPLIT_INDOOR", "Daikin", "FTX12AXVJU", "DEMO-MS-5002", "2020-06-11")]),
    ("Jennifer Lee", 0, [unit("FURNACE", "Lennox", "ML180UH070", "DEMO-FU-6001", "2018-09-21")]),
    ("David Kowalski", 0, [unit("BOILER", "Weil-McLain", "WGO-4", "DEMO-BO-7001", "2003-10-01",
                                "20+ years old - recommend replacement quote"),
                           unit("OIL_BURNER", "Riello", "40-F5", None, None, "Serial plate unreadable")]),
    ("Susan Murphy", 0, [unit("CENTRAL_AC", "Trane", "4TTR4030L1000A", "DEMO-AC-8001", "2015-05-05")]),
    ("Carlos Santos", 0, [unit("OIL_BURNER", "Beckett", "AFG", "DEMO-OB-9001", "2009-10-20"),
                          unit("WATER_HEATER", "A.O. Smith")]),
    ("Karen Fitzgerald", 0, [unit("BOILER", "Navien", "NHB-150", "DEMO-BO-1101", "2020-12-03")]),
    ("Thomas Clark", 0, [unit("HEAT_PUMP", "Mitsubishi", "PUZ-HA36NHA5", "DEMO-HP-1201", "2023-04-18",
                              "Installed 2023 - under warranty")]),
    ("Angela Martinez", 0, [unit("FURNACE", "Goodman", "GM9S960603BN", "DEMO-FU-1301", "2017-10-10"),
                            unit("CENTRAL_AC", "Goodman", "GSX140301", "DEMO-AC-1302", "2017-10-10")]),
    ("Kevin Walsh", 0, [unit("MINI_SPLIT_OUTDOOR", "Fujitsu", "AOU15RLS3H", "DEMO-MS-1401", "2019-07-01"),
                        unit("MINI_SPLIT_INDOOR", "Fujitsu", "ASU15RLS3", "DEMO-MS-1402", "2019-07-01")]),
    ("Nancy Dubois", 0, [unit("BOILER", "Peerless", "WBV-03", None, "2008-11-12")]),
    ("Brian Sullivan", 0, [unit("FURNACE", "York", "TM9E060B12MP11", "DEMO-FU-1601", "2014-10-30",
                                "Rental unit - tenant calls")]),
    ("Elena Popescu", 0, [unit("MINI_SPLIT_OUTDOOR", "Mitsubishi", "MXZ-3C24NA", "DEMO-MS-1701", "2024-06-25"),
                          unit("MINI_SPLIT_INDOOR", "Mitsubishi", "MSZ-GL09NA", "DEMO-MS-1702", "2024-06-25", "Bedroom"),
                          unit("MINI_SPLIT_INDOOR", "Mitsubishi", "MSZ-GL09NA", "DEMO-MS-1703", "2024-06-25", "Office")]),
    ("Daniel Okafor", 0, [unit("OIL_BURNER", "Beckett", "NX", "DEMO-OB-1801", "2012-09-14", "Oil tank in garage"),
                          unit("FURNACE", "Thermo Pride", "OH6", "DEMO-FU-1802", "2012-09-14")]),
    ("Rachel Goldberg", 0, [unit("HEAT_PUMP", "Daikin", "DZ6VSA3610", "DEMO-HP-1901", "2022-10-05")]),
    ("Steven Moreau", 0, [unit("BOILER", "Buderus", "G115WS", "DEMO-BO-2001", "2013-11-08")]),
    ("Steven Moreau", 1, [unit("CENTRAL_AC", "Rheem", "RA1424AJ1NA", "DEMO-AC-2003", None,
                               "Summer cottage - shut off in winter")]),
]

HEATING = {"OIL_BURNER", "BOILER", "FURNACE"}
TUNE_UP_WORK = {
    "OIL_BURNER": ("Annual tune-up: replaced nozzle and oil filter, cleaned electrodes, combustion test", "Nozzle, oil filter"),
    "BOILER": ("Annual boiler service: cleaned heat exchanger, checked pressure and relief valve, tested controls", "Gasket kit"),
    "FURNACE": ("Furnace tune-up: replaced filter, cleaned burners and flame sensor, checked heat exchanger", "16x25x1 filter"),
    "CENTRAL_AC": ("AC tune-up: cleaned condenser coil, checked refrigerant charge and capacitor, cleared condensate line", None),
    "HEAT_PUMP": ("Heat pump service: cleaned coils, checked charge and defrost cycle, tested reversing valve", None),
    "MINI_SPLIT_OUTDOOR": ("Mini-split outdoor service: cleaned coil, checked charge and connections", None),
    "MINI_SPLIT_INDOOR": ("Mini-split head cleaning: washed filters, cleaned blower wheel and coil, flushed drain", "Coil cleaner"),
    "WATER_HEATER": ("Flushed tank, checked anode rod and T&P valve", None),
}
REPAIRS = {
    "OIL_BURNER": ("No heat call: replaced cad cell and reset primary control", "Cad cell"),
    "BOILER": ("Replaced circulator pump", "Taco 007 circulator"),
    "FURNACE": ("No heat: replaced flame sensor", "Flame sensor"),
    "CENTRAL_AC": ("Not cooling: replaced run capacitor", "45/5 MFD capacitor"),
    "HEAT_PUMP": ("Iced up: replaced defrost control board", "Defrost board"),
    "MINI_SPLIT_OUTDOOR": ("Error code: replaced outdoor fan motor", "Fan motor"),
    "MINI_SPLIT_INDOOR": ("Water leak: cleared clogged drain line", None),
    "WATER_HEATER": ("No hot water: replaced thermocouple", "Thermocouple"),
}
FOLLOW_UPS = ["Recommend replacement quote - unit near end of life", "Heat exchanger showing wear - recheck next visit",
              "Customer asked about adding a zone", "Order replacement part - temporary fix in place"]

# Today's schedule: (customer, start, minutes, type, urgency, notes)
TODAY_JOBS = [
    ("David Kowalski", "08:00", 90, "TUNE_UP", "NORMAL", "Old boiler - check heat exchanger"),
    ("Elena Popescu", "11:00", 60, "SERVICE_CALL", "URGENT", "Mini-split head in office not cooling"),
    ("Kevin Walsh", "13:30", 60, "ESTIMATE_VISIT", "NORMAL", "Quote for adding a second zone"),
    ("Linda Nguyen", "15:30", 90, "TUNE_UP", "NORMAL", "Fall furnace tune-up"),
]


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.token = None

    def call(self, method, path, body=None, expect=(200, 201, 202)):
        data = json.dumps(body).encode() if body is not None else None
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        req = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req) as res:
                payload = res.read()
                status = res.status
        except urllib.error.HTTPError as e:
            payload, status = e.read(), e.code
        result = json.loads(payload) if payload else None
        if status not in expect:
            raise SystemExit(f"{method} {path} failed with {status}: {result}")
        return result


def verify_with_mailpit(api, mailpit):
    """Development only: click the newest verification link waiting in Mailpit."""
    with urllib.request.urlopen(f"{mailpit}/api/v1/search?query=" + urllib.request.quote('subject:"Verify your ServiceLog email"')) as r:
        messages = json.load(r)["messages"]
    if not messages:
        raise SystemExit("No verification email found in Mailpit")
    with urllib.request.urlopen(f"{mailpit}/api/v1/message/{messages[0]['ID']}") as r:
        text = json.load(r)["Text"]
    token = re.search(r"token=([A-Za-z0-9_-]+)", text).group(1)
    api.call("POST", "/api/auth/verify-email", {"token": token})


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", default="http://localhost:8080")
    parser.add_argument("--email", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--verify-with-mailpit", nargs="?", const="http://localhost:8025", metavar="MAILPIT_URL")
    args = parser.parse_args()

    api = Api(args.url)
    if args.verify_with_mailpit:
        verify_with_mailpit(api, args.verify_with_mailpit)
    api.token = api.call("POST", "/api/auth/login", {"email": args.email, "password": args.password})["token"]

    if api.call("GET", "/api/customers"):
        raise SystemExit("This database already has customers. The demo data is only for an empty database.")

    today = dt.date.fromisoformat(api.call("GET", "/api/jobs/today")["date"])
    rng = random.Random(42)

    # Customers
    customers = {}
    for name, last4, street, unit_no, city, zip_code, notes in CUSTOMERS:
        properties = [{"street": street, "unit": unit_no, "city": city, "state": "MA", "zip": zip_code, "notes": notes}]
        if name in SECOND_PROPERTY:
            s, c, z, n = SECOND_PROPERTY[name]
            properties.append({"street": s, "unit": None, "city": c, "state": "MA", "zip": z, "notes": n})
        first = name.split()[0].lower()
        customers[name] = api.call("POST", "/api/customers", {
            "name": name, "phone": f"413-555-{last4}", "email": f"{first}@example.com", "properties": properties})
    print(f"customers: {len(customers)}")

    # Equipment and service history
    units = records = 0
    for name, index, unit_list in EQUIPMENT:
        property_id = customers[name]["properties"][index]["id"]
        for u in unit_list:
            created = api.call("POST", f"/api/properties/{property_id}/equipment", u)["equipment"]
            units += 1
            for body in history(rng, today, created):
                api.call("POST", f"/api/equipment/{created['id']}/service-records", body)
                records += 1
    print(f"equipment: {units}, service records: {records}")

    # Today's schedule, as it looks around midday: the first job done, the second in progress
    job_ids = []
    for name, start, minutes, job_type, urgency, notes in TODAY_JOBS:
        job_ids.append(api.call("POST", "/api/jobs", {
            "propertyId": customers[name]["properties"][0]["id"], "date": today.isoformat(), "startTime": start,
            "durationMinutes": minutes, "type": job_type, "urgency": urgency, "notes": notes,
            "allowOverlap": True})["id"])
    first = TODAY_JOBS[0][0]
    first_units = [e["id"] for e in api.call("GET", f"/api/customers/{customers[first]['id']}/equipment")]
    api.call("POST", f"/api/jobs/{job_ids[0]}/complete", {
        "equipmentIds": first_units,
        "workDone": "Tune-up: cleaned boiler heat exchanger, replaced burner nozzle and filter, combustion test OK",
        "partsUsed": "Nozzle, oil filter", "followUpNeeded": True,
        "followUpNote": "Boiler is over 20 years old - send a replacement quote"})
    api.call("POST", f"/api/jobs/{job_ids[1]}/start")
    print(f"jobs today: {len(TODAY_JOBS)} (first done, second in progress)")


def history(rng, today, equipment):
    """1-4 seasonal visits per unit: an install record if recent, tune-ups (some overdue), an occasional repair."""
    t = equipment["type"]
    installed = dt.date.fromisoformat(equipment["installDate"]) if equipment["installDate"] else dt.date(2015, 1, 1)
    visits = []
    if equipment["installDate"] and installed.year >= 2019:
        visits.append((installed, "INSTALLATION", f"Installed new {equipment['typeLabel'].lower()}", None))
    if t in TUNE_UP_WORK:
        months = [9, 10] if t in HEATING else [3, 4, 5, 6, 7, 8] if t == "WATER_HEATER" else [4, 5, 6]
        last_year = rng.choice([today.year, today.year, today.year - 1, today.year - 2])
        for year in range(max(installed.year + 1, last_year - rng.randint(0, 2)), last_year + 1):
            day = dt.date(year, rng.choice(months), rng.randint(1, 28))
            if installed < day < today:
                visits.append((day, "TUNE_UP", *TUNE_UP_WORK[t]))
    if t in REPAIRS and rng.random() < 0.35:
        day = dt.date(rng.choice([today.year - 1, today.year]), rng.choice([1, 2, 7, 8]), rng.randint(1, 28))
        if installed < day < today:
            visits.append((day, "REPAIR", *REPAIRS[t]))
    for day, kind, work, parts in sorted(visits):
        follow = kind != "INSTALLATION" and rng.random() < 0.12
        yield {"serviceDate": day.isoformat(), "kind": kind, "workDone": work, "partsUsed": parts,
               "followUpNeeded": follow, "followUpNote": rng.choice(FOLLOW_UPS) if follow else None}


if __name__ == "__main__":
    sys.exit(main())
