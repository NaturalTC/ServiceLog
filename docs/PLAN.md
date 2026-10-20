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

## Roadmap
Work is planned in 2-week Scrum sprints. The prioritised backlog, user stories, sprint
plan, Definition of Done and AI approach are in **[BACKLOG.md](BACKLOG.md)**.

| Iteration | Outcome |
|---|---|
| Setup (done) | Spring Boot + PostgreSQL + Flyway backend, React + TypeScript frontend, CI |
| Sprint 0 | Owner interview ([discovery.md](discovery.md)); backlog confirmed |
| Iteration 1 (sprints 1-4) | Customer & equipment log, My Day, tune-up reminders, AI service notes, deployed |
| Trial | One month of real use by the owner; results measured |
| Iteration 2+ | Push notifications, automatic reminders, offline mode, photos, more AI |
