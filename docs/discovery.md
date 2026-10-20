# Discovery - owner interview

Goal: find the biggest time sinks before building. Record answers in his words.
Keep this file free of customer names or other personal details.

## Questions
1. Walk me through a normal day, from first call to last job.
2. What takes your time that isn't fixing equipment?
3. How do you track customers, jobs and invoices today? (paper, spreadsheet, QuickBooks, memory)
4. What fell through the cracks recently? (missed follow-up, forgotten tune-up, unpaid invoice)
5. When a customer calls, how do you find what equipment they have and what you did last time?
6. Do you remind customers about seasonal tune-ups today? How?
7. What phone do you use in the field? How is signal in basements and attics?
8. Have you tried apps like Jobber or Housecall Pro? What did you like or hate?
9. Would you try a simple app for a month if it saved you an hour a week?
10. Now that you work alone, what changed? What do you still track, and what did you drop?
11. Which tune-ups do you remind people about (oil burner, AC, mini-split cleaning), and when?
12. How often do warranty registrations or claims get delayed or forgotten?
13. Do you want anything from QuickBooks in this app, or should they stay separate?

## Prior discovery (December 2025 database course project)
An earlier interview with the owner, a shop visit and an 18-table MySQL design. At the time
the shop had employees; it is now a one-person business, so payroll and employee tracking
no longer apply. The design was deliberately not deployed: it wasn't practical for daily use.

What the owner said he tracks:
- **Equipment model and serial numbers**, because every installed unit must be registered
  for warranty. He submits warranty claims for customers and offers extended warranties
  through a manufacturer dealer program.
- **Annual tune-ups** (oil burner service in particular) for returning customers.
- **Service calls with urgency and status.**
- **Estimates**, which go stale: material prices change, so estimates need updating after
  about 4 months.
- **Installs:** customer pays 50% up front, the rest on completion. **Service calls:**
  one-hour minimum, with approval before going longer.
- **Inventory** of install materials (line set, line hide, copper, armor cable, condensate
  tube, stands, patio blocks, refrigerant, nitrogen).
- **Customers, estimates, invoices and payments live in QuickBooks** (customer profiles,
  notes, due dates, online payment).

Lessons from that project:
- Equipment at a customer's home was not its own entity (serials sat on job parts), so
  "what's installed at this house?" was hard to answer. ServiceLog makes equipment central.
- Trying to model the whole business (payroll, purchasing, invoicing) made it too big to
  use. ServiceLog stays narrow and leaves invoicing to QuickBooks.

## Answers (sprint 0 follow-up, now that he works alone)
_(fill in after the conversation)_

## Top 3 problems to solve
1.
2.
3.
