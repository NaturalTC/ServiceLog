# ServiceLog - Project Plan

**What:** HVAC service history and tune-up reminders for one-person shops.
A mobile-first, installable web app (PWA) for a solo HVAC technician: today's jobs at a
glance, every customer's equipment and service history on site, and automatic seasonal
tune-up reminders to past customers.

**First user:** the owner of a one-person HVAC business in western Massachusetts.

**Stack:** React + TypeScript (Vite, PWA) · Spring Boot (Java 21) · PostgreSQL with Flyway
migrations · Docker Compose · GitHub Actions · AWS later.

**Ground rules**
- Build for the real user. Every feature traces back to something he said (docs/discovery.md).
- Phone first: one hand, gloves, bad signal. Common actions in 3 taps or fewer.
- Customer data is real personal data: never commit it, never use it in demos or screenshots.

---

## Milestone 0 - Setup (done)
- [x] Spring Boot backend (web, JPA, PostgreSQL, Flyway, validation, actuator)
- [x] React + TypeScript frontend with a dev proxy to the backend
- [x] PostgreSQL in Docker Compose (port 5433), CI for both halves

## Milestone 1 - Discovery (talk to the owner)
- [ ] Interview the owner with the questions in docs/discovery.md
- [ ] Write down his top 3 time sinks and how he tracks work today
- [ ] Confirm v1 scope below (or change it)
- [ ] Agree on how he'll try it (which phone, for how long)

## Milestone 2 - Customers and equipment (the "log")
- [ ] Data model + Flyway migration: customers, properties, equipment, service records
- [ ] REST API with validation and tests
- [ ] Mobile screens: search customers, view equipment and service history, add a service record

## Milestone 3 - "My day" (time management)
- [ ] Jobs table + API: schedule, reorder, status (scheduled / in progress / done)
- [ ] Today view: ordered jobs, tap to navigate, tap to call; week view
- [ ] Finishing a job writes a service record automatically

## Milestone 4 - Reminders
- [ ] Owner reminders: upcoming job notifications (web push)
- [ ] Customer tune-up reminders: find equipment due by season and last service date
- [ ] Reliable scheduled sending that survives restarts; email to customers
- [ ] Opt-out link for customers; log of every reminder sent

## Milestone 5 - Field-ready PWA
- [ ] Installable on the home screen
- [ ] Works offline; queues changes and syncs when back online
- [ ] Login (single owner account) and HTTPS

## Milestone 6 - Deploy and trial
- [ ] Deploy (AWS: EC2 or similar + RDS, frontend on S3/CloudFront) via GitHub Actions
- [ ] One-month trial with the owner; measure time saved and reminders sent
- [ ] Write up results for the README (anonymised)

## Later (only if he asks)
- Quotes and invoices, text-message reminders, payments
