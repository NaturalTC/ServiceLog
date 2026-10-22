# ServiceLog

HVAC service history and tune-up reminders for one-person shops.

ServiceLog is a mobile-first field service management app built with and for a
solo HVAC technician: today's jobs at a glance, each customer's equipment and service
history on site, and automatic seasonal tune-up reminders to past customers.

**Status:** early development. See the [project plan](docs/PLAN.md), [backlog](docs/BACKLOG.md) and
[product vision](docs/VISION.md).

## Project structure
The backend is organized **by feature**, following the
[Spring Boot reference guide](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html):
each feature package holds its own entity, repository, service, controller and DTOs, so a change
to one feature stays in one place. Code shared across features lives in its own top-level packages.

```
backend/src/main/java/dev/servicelog/
├── ServicelogApplication.java   main class in the root package, so component scanning covers everything
├── auth/            owner login, email verification, password reset
├── customer/        customers, properties, phone numbers, customer search
├── equipment/       equipment at a property (types, serial numbers)
├── servicerecord/   service visits on a piece of equipment, service history
├── job/             scheduling, rescheduling, double-booking check
├── security/        JWT filter and Spring Security configuration (shared)
├── config/          app-wide beans: clock, async email, business time zone (shared)
└── exception/       shared exceptions and the JSON error handler
backend/src/main/resources/db/migration/   Flyway migrations (V1, V2, ...)
frontend/src/
├── api/             typed API clients, one per feature
├── auth/            login state
├── components/      shared layout
├── lib/             formatting and small helpers
└── pages/           one folder per feature: customers, equipment, service, jobs
```

Tests mirror the main packages under `backend/src/test/java/dev/servicelog/`.

## Stack
React + TypeScript (Vite, PWA) · Spring Boot (Java 21) · PostgreSQL + Flyway ·
Docker Compose · GitHub Actions

## Run locally
Requires Java 21+, Node 22+ and Docker.

```bash
docker compose up -d                 # PostgreSQL on :5433, Mailpit (dev inbox) on :8025

cp backend/.env.example backend/.env # then fill it in:
#   JWT_SECRET      a long random string: openssl rand -base64 48
#   OWNER_EMAIL     the owner's login email
#   OWNER_PASSWORD  12+ characters; only used to create the account on first start

cd backend && ./mvnw spring-boot:run # API on :8080
cd frontend && npm install && npm run dev   # app on :5173
```

There is no sign-up page: the single owner account is created on first startup from
`OWNER_EMAIL` and `OWNER_PASSWORD`, and a verification email is sent. Remove
`OWNER_PASSWORD` from `.env` afterwards.

**Emails in development** (verification, password reset) never reach real inboxes: Mailpit
catches them all. Open http://localhost:8025 to read them and click their links.

## Tests
```bash
cd backend && ./mvnw verify          # needs `docker compose up -d`; uses its own servicelog_test database
cd frontend && npm run lint && npm run build
```
