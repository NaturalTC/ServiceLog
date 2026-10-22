# ServiceLog - Product Vision

## Where it's going
ServiceLog starts as the service log for one HVAC technician: customers, equipment, service
history, the day's jobs and seasonal tune-up reminders. Over time it becomes an **AI back
office for small trade businesses**. It plans jobs, drafts quotes and invoices, and keeps
the books, while the tradesperson stays on the tools.

## What makes it different
General trade apps treat every job the same. ServiceLog is built around **the equipment at
each address**: its model, serial number, warranty, age and full service history. Every
later feature uses that. A quote knows the existing unit is a 14-year-old oil burner, a
tune-up reminder knows when it was last serviced, and an AI job plan knows what's in the
basement before anyone arrives.

## Principles
- **Built with a real user.** Each phase starts from what the owner says he needs.
- **Work with the tools people already use.** Sync with QuickBooks rather than replace it.
- **AI drafts, people decide.** Every AI-generated plan, quote or message is reviewed
  before it's saved or sent, and AI features are measured for accuracy before release.
- **Honest about limits.** Code and sizing guidance is labelled as guidance, never as
  certification.

## Roadmap

| Phase | Theme | Features |
|---|---|---|
| **v1** (now) | The service log | Customers and properties, equipment, service history, My day, tune-up reminders, AI service notes, owner trial |
| **v2** | Getting paid | Quotes and PDF invoices from service records, QuickBooks sync (invoices out, paid status back), estimate expiry reminders |
| **v3** | AI assistant | AI job planning from a description, photo analysis of the job site, AI-drafted quotes with labor/materials/tax breakdown, follow-up suggestions from equipment history |
| **v4** | Growth | Customer intake link (customer describes the job and uploads photos, no site visit needed), material price lookup, local directory listing, built-in income and expense tracking |
| **Any time** | Free tools | Public calculators: HVAC sizing estimate, hourly rate, profit margin, material markup, labor cost |

Details and status: [BACKLOG.md](BACKLOG.md), epics E7-E10.

## Risks to manage
- **Compliance and sizing advice.** Building codes vary by town, and a proper load
  calculation (Manual J) needs many inputs. Treat these as clearly labelled guidance.
- **Material prices.** Suppliers rarely publish clean price feeds, so price lookup may need
  manual price lists or supplier partnerships.
- **Scope.** Each v4 item is close to a product of its own. Finish and prove each phase
  with the real user before starting the next.
