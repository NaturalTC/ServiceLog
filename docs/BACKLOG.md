# ServiceLog - Agile Plan and Product Backlog

## How we work
- **Framework:** Scrum, adapted for a one-developer team.
- **Sprint length:** 2 weeks. Assumed capacity ~10 hours/week, so ~20 hours per sprint.
- **Product owner / stakeholder:** the shop owner (first real user). He sees a demo at the
  end of every sprint and decides what matters next.
- **Ceremonies (lightweight):**
  - *Sprint planning* (30 min): pull the top stories from the backlog that fit the sprint.
  - *Weekly check-in* (15 min, written in the sprint log): done / next / blocked.
  - *Sprint review* (30 min): demo to the owner on his phone; record his feedback.
  - *Retrospective* (15 min): one thing to keep, one to change.
- **Estimation:** story points (1, 2, 3, 5, 8). Track velocity after sprint 1 and adjust.
- **Prioritisation:** MoSCoW (Must / Should / Could / Won't for now), ordered by value to
  the owner divided by effort. The backlog is provisional until discovery (sprint 0) is done.
- **Board:** GitHub Projects with columns Backlog → Ready → In progress → Review → Done;
  one GitHub issue per story.

### Definition of Ready (before a story enters a sprint)
- Written as a user story with acceptance criteria
- Estimated, and small enough to finish in one sprint (8 points max; split otherwise)
- Traces back to something the owner said, or is a technical enabler for one that does

### Definition of Done
- Acceptance criteria met and demoed on a phone-sized screen
- Backend tests for new logic and endpoints; CI green
- Database changes are Flyway migrations
- No customer data in the repo, logs, screenshots or AI test sets
- Merged to `main` through a pull request

---

## Personas
- **The owner (primary):** runs a one-person HVAC business. Works from his phone, often in
  basements and attics with poor signal and dirty hands. Wants less paperwork and more
  repeat business.
- **The customer (secondary):** a homeowner. Wants a heads-up before seasonal maintenance
  and an easy way to book.

---

## Epics
| ID | Epic | Why it matters |
|---|---|---|
| E1 | Customer & equipment log | Know what's on site and what was done last time |
| E2 | My Day (time management) | Today's jobs in order, one tap to navigate or call |
| E3 | Reminders | Never miss a job or a seasonal tune-up; repeat business |
| E4 | AI assistance | Less typing in the field; smarter follow-ups |
| E5 | Field-ready app | Installable, works offline, secure |
| E6 | Deploy & trial | Real usage and measurable results |
| E7 | Quotes & invoices (v2) | Get paid faster; syncs with QuickBooks instead of replacing it |
| E8 | AI assistant (v3) | Job plans and quote drafts from a description and photos |
| E9 | Growth (v4) | Customer intake link, price lookup, directory, income and expenses |
| E10 | Free trade calculators | Small public tools: useful on their own and good for the landing page |

E7-E10 are the longer-term direction in [VISION.md](VISION.md). They don't have detailed stories
yet; those get written when a phase starts, from what the owner says he needs.

---

## Product backlog (prioritised)

### Must have - Iteration 1 (MVP)
| ID | Story | Epic | Pts |
|---|---|---|---|
| S1 | As the owner, I can log in with my own account so customer data stays private. | E5 | 3 |
| S2 | As the owner, I can add and edit a customer with address and phone in under 30 seconds. | E1 | 3 |
| S3 | As the owner, I can search customers by name, street or phone and get results as I type. | E1 | 3 |
| S4 | As the owner, I can record a customer's equipment (type, brand, model, serial, install date). | E1 | 3 |
| S5 | As the owner, I can add a service record (date, work done, parts, notes) to a piece of equipment. | E1 | 3 |
| S6 | As the owner, I can see one piece of equipment's full service history, newest first. | E1 | 2 |
| S7 | As the owner, I can schedule a job for a customer on a date and time, with a type (install, service call, tune-up, estimate visit) and urgency. | E2 | 3 |
| S8 | As the owner, I open the app to today's jobs in order, and tap to navigate or call. | E2 | 5 |
| S9 | As the owner, I can mark a job done, and that creates the service record automatically. | E2 | 3 |
| S10 | As the owner, I see a list of customers due for a seasonal tune-up (oil burner / heating in fall, AC and mini-split in spring), based on equipment type and last service date. | E3 | 5 |
| S11 | As the owner, I can send a tune-up reminder email to a due customer with one tap. | E3 | 5 |
| S12 | As a customer, I can unsubscribe from reminders with one link. | E3 | 2 |

### Should have - Iteration 1 if capacity allows, otherwise Iteration 2
| ID | Story | Epic | Pts |
|---|---|---|---|
| S24 | As the owner, I can record warranty details on installed equipment (registered yes/no, registration date, warranty and extended-warranty end dates) and see units not yet registered. | E1 | 3 |
| S13 | As the owner, I can dictate service notes, and AI turns them into a structured service record (work done, parts, readings, follow-up) that I review before saving. | E4 | 8 |
| S14 | As the developer, I have an evaluation set of 30+ synthetic service notes with expected fields, and measure the AI's extraction accuracy before shipping S13. | E4 | 5 |
| S15 | As the owner, I can install the app on my phone's home screen. | E5 | 2 |
| S16 | As the owner, the app is deployed online with HTTPS so I can use it on a job. | E6 | 5 |

### Could have - Iteration 2+
| ID | Story | Epic | Pts |
|---|---|---|---|
| S17 | As the owner, I get a phone notification before each job (web push). | E3 | 5 |
| S18 | As the owner, tune-up reminders send automatically on a schedule I set, and I can see a log of everything sent. | E3 | 5 |
| S19 | As the owner, AI drafts a personalised reminder email from the customer's equipment history; I approve before sending. | E4 | 3 |
| S20 | As the owner, I can ask "what did I do at this house last time?" and get an answer from the service history. | E4 | 5 |
| S21 | As the owner, the app works offline in a basement and syncs when I'm back online. | E5 | 8 |
| S22 | As the owner, I can attach photos to a service record. | E1 | 5 |
| S23 | As the owner, AI suggests likely causes for a symptom, using the equipment model and its history (suggestions only, clearly labelled). | E4 | 8 |
| S25 | As the owner, I'm reminded before a warranty or extended warranty expires so I can offer service or a renewal. | E3 | 3 |
| S26 | As the owner, I'm reminded when an estimate is about 4 months old so I can re-price it before the customer accepts. | E3 | 3 |
| S27 | As the owner, I can track stock of my most-used install materials and see what's running low. | E1 | 5 |

### UI polish (sprint 5, before the trial)
| ID | Story | Epic | Pts |
|---|---|---|---|
| S28 | As the owner, every screen looks clean and consistent (one design system, light and dark). | E5 | 3 |
| S29 | As the owner, navigation feels like a phone app (tab icons, slim header, safe areas). | E5 | 2 |
| S30 | As the owner, every screen uses the new look, with the most important thing first. | E5 | 5 |
| S31 | As a visitor, the landing page matches the app and shows current screenshots. | E5 | 3 |

### Later phases (see VISION.md)
- **E7 (v2):** quotes and PDF invoices built from service records, synced with QuickBooks
  (the owner already uses it for billing, so we connect to it rather than replace it).
- **E8 (v3):** AI job planning, photo analysis and AI-drafted quotes, reviewed before sending.
- **E9 (v4):** customer intake link, material price lookup, directory listing, income and expenses.
- **E10 (any time):** public calculators (HVAC sizing estimate, hourly rate, profit margin, markup).

### Won't have (for now)
Payroll and employees (the owner works alone), text-message reminders, dispatch.
Revisit only if the owner asks.

---

## Iteration 1 - sprint plan (draft, ~20 points per sprint)
| Sprint | Goal | Stories | Pts |
|---|---|---|---|
| 0 (1 week) | Discovery: interview the owner, confirm or reorder this backlog, set up the board | - | - |
| 1 | "I can look up any customer's equipment" | S1, S2, S3, S4, S5, S6 | 17 |
| 2 | "My day on my phone" | S7, S8, S9, S15 | 13 |
| 3 | "Customers come back for tune-ups" | S10, S11, S12, S16 | 17 |
| (3 or 4, if capacity) | Warranty tracking | S24 | 3 |
| 4 | "Less typing in the field" (AI) | S14, S13 | 13 |
| 5 | "Looks like a real product": UI polish before the trial | S28, S29, S30, S31 | 13 |
| - | **Trial starts:** owner uses it on real jobs for a month | | |

Sprint 2 is light on purpose: S8 is the first real mobile UI and is likely to take longer
than estimated. Re-plan every sprint from actual velocity.

---

## AI assistance - approach
- **No model training.** Use a hosted LLM API with structured output (a JSON schema for a
  service record), with prompts that include only the context the task needs.
- **Human in the loop:** AI only drafts. The owner reviews and confirms every record and
  every customer message before it is saved or sent.
- **Measure before shipping (S14, done):** 36 synthetic notes with traps, field-level
  scoring with hallucinated parts counted separately, and an agreed bar enforced by
  `ai-eval/evaluate.py --check`. See [AI-EVALUATION.md](AI-EVALUATION.md).
- **Privacy:** never send customer names, addresses or phone numbers to the AI provider;
  send only the note text and equipment details. The owner agrees to this before S13 ships.
- **Cost guardrails:** cap requests per day; log token usage.
- **Failure handling:** if the AI is down or unsure, fall back to the normal manual form.

## Metrics for the trial
- Minutes per service record (manual vs AI-assisted)
- Tune-up reminders sent → jobs booked from them
- Weekly active use by the owner
- Owner's own rating after a month: would he keep using it?
