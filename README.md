## Nexus

Production‑ready Clojure web server skeleton focused on clarity and good defaults. It uses Integrant for system wiring, Reitit + Malli for routing and schemas, next.jdbc + HoneySQL for data access, and Buddy for JWT auth. A tiny ClojureScript UI is bundled via Shadow‑CLJS.

### Features
- **HTTP**: Reitit router, Ring Jetty server, CORS, content negotiation, exception handling
- **Schemas & Docs**: Malli coercion + generated OpenAPI with Swagger UI at `/api/docs`
- **Auth**: JWT via `Authorization: Bearer <token>` or cookie `auth-token`
- **Users module**: register, login, list/search, get, update, delete, change password
- **DB**: Postgres with HikariCP, HoneySQL, migrations via Migratus
- **Config**: Aero profiles with `resources/envs/{dev,prod}.edn`
- **Frontend (optional)**: Shadow‑CLJS build to `resources/public/js`

### Quick start
Prereqs: JDK 11+, Leiningen, Node 18+, Docker (for Postgres/dev tests).

1) Start Postgres locally
```bash
docker compose up -d
```

2) Configure dev env
- `resources/envs/dev.edn` points to `postgres://postgres:postgres@localhost:5436/nexus` and port `3456`.

3) Run DB migrations
```bash
lein run migrate
```

4) Start the app (dev via REPL for hot‑reload)
```bash
lein repl
;; then in the REPL
(require 'nexus.user)
(nexus.user/start)
```
Server runs on `http://localhost:3456`. Swagger UI: `http://localhost:3456/api/docs`.

Prod (env‑driven) run:
```bash
DB_URL=postgres://... JWT_SECRET=... PORT=8080 lein run
```

Build uberjar (includes frontend build):
```bash
lein uberjar-full
java -jar target/uberjar/nexus-0.1.0-SNAPSHOT-standalone.jar
```

### API (essentials)
- **Health**: `GET /api/health`
- **OpenAPI**: `GET /api/openapi.json`, UI at `/api/docs`
- **Auth**: `GET /api/auth/identity` (requires Bearer or cookie)
- **Users (public)**:
  - `POST /api/users/register`
  - `POST /api/users/login`
- **Users (authenticated)**:
  - `GET /api/users`
  - `GET /api/users/:id`
  - `PATCH /api/users/:id`
  - `DELETE /api/users/:id`
  - `POST /api/users/:id/change-password`
- **Examples**: `POST /api/files/upload`, `GET /api/files/download`, `GET /api/math/plus`

Web routes (cookie‑based auth):
- `POST /auth/login`, `POST /auth/logout`, `GET /auth/me`
- Static homepage: `GET /` (serves `resources/public/index.html`)

### Project structure
- `src/clj/nexus/system.clj` — config loading (Aero) and bootstrapping (Integrant)
- `src/clj/nexus/server.clj` — Ring/Jetty, middleware stack, Swagger UI
- `src/clj/nexus/router/*` — `api.clj`, `web.clj`, `core.clj` route composition
- `src/clj/nexus/users/*` — schemas, queries (HoneySQL), service, HTTP handlers
- `src/clj/nexus/auth/*` — JWT service and auth middleware
- `src/clj/nexus/db.clj` — HikariCP pool, exec helpers, migrations component
- `resources/envs/*.edn` — environment config; `system.config.edn` wires components
- `resources/migrations` — SQL migrations (Migratus)
- `dev/nexus/user.clj` — REPL helpers: `(nexus.user/start)`, `(nexus.user/restart)`
- `resources/public` — static assets and compiled CLJS

### Database & migrations
- Connection comes from `resources/envs/{dev,prod}.edn` via `:db-url`.
- Run migrations anytime with `lein run migrate`.

### Frontend (optional)
```bash
npm install
# build CLJS bundle -> resources/public/js
npm run build
# build CSS (Tailwind)
npm run build:css
```

### Testing
```bash
lein test
```
Integration tests use Testcontainers; ensure Docker is running.

### License
Copyright © 2025 RegiByte

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.