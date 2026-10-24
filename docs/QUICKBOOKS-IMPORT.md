# Moving customers over from QuickBooks

ServiceLog leaves invoicing to QuickBooks, but the customer list is already there, so
`scripts/quickbooks_import.py` copies it over once instead of retyping it. It reads an
export file and creates the customers through ServiceLog's API. Nothing in QuickBooks
changes, and the app itself has no import screen.

## 1. Export the customer list

**QuickBooks Online:** Customers (Customer Hub > Customers & leads) > the **Export to Excel**
icon next to Print. Or Reports > **Customer Contact List** > Export to Excel, after adding
the Phone, Email, Billing address and Shipping address columns under Customize.

**QuickBooks Desktop:** Customer Center > **Excel** > **Export Customer List**, saved as
.xlsx or .csv. (Reports > Customers & Receivables > Customer Contact List also works.)

An old-style `.xls` file needs saving as `.xlsx` or `.csv` first.

## 2. Preview

```
python3 scripts/quickbooks_import.py Customers.xlsx --url https://<servicelog address> --email <owner login>
```

It asks for the password (or reads `SERVICELOG_PASSWORD`), creates nothing, and prints:

- **Ready to import:** customers with a name, a valid U.S. phone and a complete service address
- **Already in ServiceLog:** same phone, same email, or same name at the same street (skipped, so running it again is safe)
- **Merged:** rows that are the same customer (QuickBooks Desktop sub-jobs like `Moreau, Steven:Cottage`, or a second property) become one customer with several addresses
- **Inactive in QuickBooks:** skipped unless `--include-inactive`
- **Need details:** written to `quickbooks-needs-fixing.csv` with what's missing

## 3. Import

Run the same command with `--apply`.

## 4. Fix the rest

Open `quickbooks-needs-fixing.csv`, fill in the blank phone or address cells (the original
QuickBooks values are in the last two columns), save, and run the script on that file with
`--apply`. A missing state is filled in from a Massachusetts ZIP code.

## How columns are read

Columns are matched by name, so their order, extra columns and the report's title rows
don't matter:

| ServiceLog | QuickBooks columns it reads |
|---|---|
| Name | Customer / Customer full name / Display name / Name, or First + Last name, or Company. `Smith, John` becomes `John Smith`. |
| Phone | Phone, Phone numbers, Main Phone, Mobile, Home/Work/Alt. Phone: the first valid U.S. number |
| Email | Email / Main Email |
| Service address | Shipping address (where the equipment is) if there is one, else Billing address; one-cell or `Bill to 1`–`5` / `Ship to 1`–`5` lines. Unit numbers (Apt, Unit, Suite, #) go in their own field. |
| Notes | Notes; a company name different from the customer name is added as `Company: ...` |

Real customer files stay out of git (`.gitignore` covers `*.xlsx` and the fix-up file).
