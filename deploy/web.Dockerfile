# Web image: build the React app, then serve it with Caddy, which also gets and renews
# the HTTPS certificate and forwards /api to the backend.
# Build from the repo root: docker build -f deploy/web.Dockerfile .

FROM node:22-alpine AS build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

FROM caddy:2.11-alpine
COPY --from=build /app/dist /srv
COPY deploy/Caddyfile /etc/caddy/Caddyfile
