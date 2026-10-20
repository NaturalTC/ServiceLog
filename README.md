# ServiceLog

HVAC service history and tune-up reminders for one-person shops.

ServiceLog is a mobile-first field service management app built with and for a
solo HVAC technician: today's jobs at a glance, each customer's equipment and service
history on site, and automatic seasonal tune-up reminders to past customers.

**Status:** early development. See the [project plan](docs/PLAN.md).

## Stack
React + TypeScript (Vite, PWA) · Spring Boot (Java 21) · PostgreSQL + Flyway ·
Docker Compose · GitHub Actions

## Run locally
Requires Java 21+, Node 22+ and Docker.

```bash
docker compose up -d                 # PostgreSQL on localhost:5433
cd backend && ./mvnw spring-boot:run # API on :8080
cd frontend && npm install && npm run dev   # app on :5173
```

## Tests
```bash
cd backend && ./mvnw verify          # needs the database running
cd frontend && npm run lint && npm run build
```
