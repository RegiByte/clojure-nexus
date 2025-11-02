# Nexus

> A production-ready, full-stack web application starter built with Clojure and React/TypeScript. Designed for learning modern web development patterns and as a foundation for building real-world applications.

[![Clojure](https://img.shields.io/badge/Clojure-1.11.1-blue.svg)](https://clojure.org/)
[![License](https://img.shields.io/badge/License-EPL%202.0-green.svg)](https://www.eclipse.org/legal/epl-2.0/)

## 📖 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Quick Start](#-quick-start)
- [Architecture](#-architecture)
- [API Reference](#-api-reference)
- [Project Structure](#-project-structure)
- [Database](#-database)
- [Testing](#-testing)
- [Production Deployment](#-production-deployment)
- [Development Tools](#-development-tools)
- [Tech Stack Deep Dive](#-tech-stack-deep-dive)
- [Learning Resources](#-learning-resources)
- [Contributing](#-contributing)
- [Troubleshooting](#-troubleshooting)
- [License](#-license)

## 🎯 Overview

Nexus demonstrates production-ready architecture patterns in the Clojure ecosystem, featuring:

- **Component-based architecture** with Integrant for lifecycle management
- **Data-driven routing** with Reitit and schema validation via Malli
- **Type-safe APIs** with auto-generated OpenAPI/Swagger documentation
- **Dual authentication** supporting both JWT tokens and cookie-based sessions
- **Modern frontend** built with React, TypeScript, and TanStack Router/Query
- **Clean architecture** with clear separation between HTTP, service, and data layers
- **Developer-friendly** REPL-driven development with hot-reloading

Perfect for:

- 📚 Learning Clojure web development best practices
- 🚀 Starting new web applications with solid foundations
- 🏗️ Reference implementation for architecture patterns
- 🎓 Understanding full-stack Clojure/TypeScript integration

## ✨ Features

### Backend (Clojure)

- **HTTP Server**: Ring + Jetty with comprehensive middleware stack
- **Routing**: Reitit data-driven routing with nested route composition
- **Validation**: Malli schema validation with automatic coercion
- **Database**: PostgreSQL with HikariCP connection pooling, next.jdbc, and HoneySQL
- **Migrations**: Migratus for version-controlled database schema management
- **Authentication**: Buddy-based JWT tokens + bcrypt password hashing
- **API Documentation**: Auto-generated OpenAPI 3.0 spec with Swagger UI
- **Configuration**: Aero for environment-based configuration (dev/prod)
- **Logging**: Telemere structured logging
- **Testing**: Comprehensive test suite with Testcontainers for integration tests

### Frontend (React/TypeScript)

- **Framework**: React 18 with TypeScript for type safety
- **Routing**: TanStack Router with file-based routing and route guards
- **State Management**: TanStack Query for server state caching and synchronization
- **HTTP Client**: Axios with automatic cookie handling
- **UI Components**: Shadcn/ui component library
- **Build Tool**: Vite for fast development and optimized production builds
- **Styling**: Tailwind CSS for utility-first styling

### User Management Module

Complete user management system with:

- User registration with validation
- Login/logout with JWT or cookie-based sessions
- Password hashing with bcrypt
- User CRUD operations (list, get, update, soft delete)
- Search functionality
- Password change with old password verification
- Protected routes requiring authentication

## 🚀 Quick Start

### Prerequisites

- **JDK 11+** (Java Development Kit)
- **Leiningen** (Clojure build tool)
- **Node.js 18+** and **pnpm** (for frontend)
- **Docker** (for PostgreSQL and integration tests)

### 1. Clone and Setup

```bash
git clone git@github.com:RegiByte/clojure-nexus.git
cd nexus
```

### 2. Start PostgreSQL

```bash
docker compose up -d
```

This starts PostgreSQL on port `5436` (mapped from container port 5432).

### 3. Configure Environment

The default dev configuration should be created in `resources/envs/dev.edn`:

```clojure
{:db-url "postgres://postgres:postgres@localhost:5436/nexus"
 :port 3456
 :jwt-secret "some-super-secret-used-for-jwt-auth"}
```

For production, the default file is located at `resources/envs/prod.edn` and uses environment variables to configure the application.

### 4. Run Database Migrations

```bash
lein run migrate
```

This creates the `nexus` schema and `users` table with necessary indexes and triggers.

### 5. Start the Backend (REPL Development)

```bash
lein repl
```

Then in the REPL:

```clojure
;; Load the dev namespace
(require 'nexus.user)

;; Start the system
(nexus.user/start)

;; The server is now running on http://localhost:3456
;; Swagger UI available at http://localhost:3456/api/docs

;; Make changes to your code, then:
(nexus.user/restart)  ; Reload and restart

;; Stop the system
(nexus.user/stop)
```

### 6. Start the Frontend (Optional)

```bash
cd frontend
pnpm install
pnpm dev
```

Frontend dev server runs on `http://localhost:3000` and proxies API requests to the backend.

### 7. Try It Out!

**Via Swagger UI**: Navigate to `http://localhost:3456/api/docs`

**Via curl**:

```bash
# Health check
curl http://localhost:3456/api/health

# Register a user
curl -X POST http://localhost:3456/api/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john@example.com",
    "password": "securepass123"
  }'

# Login
curl -X POST http://localhost:3456/api/users/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "securepass123"
  }'
```

## 🏗️ Architecture

### System Architecture

Nexus uses **Integrant** for component-based architecture with dependency injection:

```
┌─────────────────────────────────────────────────┐
│  nexus.system/env (Configuration)               │
│  ├─ dev.edn / prod.edn                          │
│  └─ Aero profiles                               │
└────────────┬────────────────────────────────────┘
             │
             ├──► nexus.db/spec (DB Config)
             │    └──► nexus.db/connection (HikariCP Pool)
             │         └──► nexus.db/migrations (Migratus)
             │
             ├──► nexus.auth/jwt (JWT Service)
             │
             └──► nexus.server/app (Ring Handler)
                  └──► nexus.server/server (Jetty)
```

All components are defined in `resources/system.config.edn` and automatically started/stopped in dependency order.

### Backend Layer Architecture

```
┌──────────────────────────────────────────────────┐
│  HTTP Layer (Ring Handlers)                     │
│  - Request/response transformation               │
│  - Parameter extraction                          │
│  - Response formatting (camelCase)               │
└────────────┬─────────────────────────────────────┘
             │
┌────────────▼─────────────────────────────────────┐
│  Service Layer                                   │
│  - Business logic                                │
│  - Malli schema validation                       │
│  - Error handling                                │
│  - Transaction coordination                      │
└────────────┬─────────────────────────────────────┘
             │
┌────────────▼─────────────────────────────────────┐
│  Query Layer (HoneySQL)                          │
│  - SQL query construction                        │
│  - Query composition                             │
└────────────┬─────────────────────────────────────┘
             │
┌────────────▼─────────────────────────────────────┐
│  Database Layer (next.jdbc)                      │
│  - Connection pooling (HikariCP)                 │
│  - Query execution                               │
│  - snake_case ↔ kebab-case conversion            │
└──────────────────────────────────────────────────┘
```

### Middleware Stack

Middleware is applied in this order (bottom to top execution):

1. **Security Headers** - X-Frame-Options, Content-Type-Options
2. **Content Type** - Set Content-Type headers
3. **Cookies** - Parse and set cookies
4. **Context Injection** - Add DB, JWT service to request
5. **JWT Authentication** - Extract and verify tokens (header or cookie)
6. **Parameters** - Parse query strings and form data
7. **Content Negotiation** - Handle Accept headers
8. **Response Formatting** - Encode responses (JSON, EDN, Transit)
9. **Exception Handling** - Catch and format errors
10. **Request Parsing** - Decode request bodies
11. **Coercion** - Validate and coerce via Malli schemas
12. **Multipart** - Handle file uploads
13. **CORS** - Cross-origin resource sharing

### Authentication Flow

Nexus supports **dual authentication modes**:

#### 1. JWT Bearer Token (for APIs)

```
Client                          Server
  │                               │
  ├─── POST /api/users/login ────►│
  │    {email, password}          │
  │                               │
  │◄─── 200 OK ───────────────────┤
  │    {token: "eyJ...", user}    │
  │                               │
  ├─── GET /api/users ───────────►│
  │    Authorization: Bearer eyJ...│
  │                               │
  │◄─── 200 OK ───────────────────┤
  │    [users...]                 │
```

#### 2. Cookie-based Session (for web browsers)

```
Browser                         Server
  │                               │
  ├─── POST /auth/login ─────────►│
  │    {email, password}          │
  │                               │
  │◄─── 200 OK ───────────────────┤
  │    Set-Cookie: auth-token=... │
  │                               │
  ├─── GET /auth/me ─────────────►│
  │    Cookie: auth-token=...     │
  │                               │
  │◄─── 200 OK ───────────────────┤
  │    {user}                     │
```

### Frontend Architecture

```
┌──────────────────────────────────────────────────┐
│  TanStack Router (File-based routing)            │
│  - Route guards (auth/guest)                     │
│  - Loader functions                              │
└────────────┬─────────────────────────────────────┘
             │
┌────────────▼─────────────────────────────────────┐
│  React Components                                │
│  - Functional components with hooks              │
│  - Shadcn/ui components                          │
└────────────┬─────────────────────────────────────┘
             │
┌────────────▼─────────────────────────────────────┐
│  TanStack Query (Server State)                   │
│  - Caching & synchronization                     │
│  - Mutations                                     │
│  - Optimistic updates                            │
└────────────┬─────────────────────────────────────┘
             │
┌────────────▼─────────────────────────────────────┐
│  Axios API Client                                │
│  - HTTP requests                                 │
│  - Cookie handling (withCredentials)             │
│  - Error handling                                │
└──────────────────────────────────────────────────┘
```

## 📡 API Reference

### Core Endpoints

#### Health & Documentation

- `GET /api/health` - Health check endpoint
- `GET /api/openapi.json` - OpenAPI 3.0 specification
- `GET /api/docs` - Interactive Swagger UI documentation

#### Authentication (JWT)

- `GET /api/auth/identity` - Get current user identity from JWT token (requires auth)

#### Users - Public Endpoints

- `POST /api/users/register` - Register a new user account

  ```json
  {
    "firstName": "John",
    "lastName": "Doe",
    "middleName": "Michael",
    "email": "john@example.com",
    "password": "securepass123"
  }
  ```

- `POST /api/users/login` - Login and receive JWT token
  ```json
  {
    "email": "john@example.com",
    "password": "securepass123"
  }
  ```

#### Users - Protected Endpoints (require authentication)

- `GET /api/users` - List all users (with pagination)
  - Query params: `offset` (default: 0), `limit` (default: 50)
- `GET /api/users/search?q=term` - Search users by name or email
- `GET /api/users/:id` - Get user by ID
- `PATCH /api/users/:id` - Update user information
- `DELETE /api/users/:id` - Soft delete a user
- `POST /api/users/:id/change-password` - Change user password

#### Web Routes (Cookie-based Authentication)

- `POST /auth/login` - Login with cookie session
- `POST /auth/logout` - Logout and clear cookie
- `GET /auth/me` - Get current authenticated user
- `GET /` - Serve frontend SPA

#### Example Endpoints

- `GET /api/math/plus?x=1&y=2` - Example query parameters
- `POST /api/math/plus` - Example body parameters
- `POST /api/files/upload` - Example file upload (multipart)
- `GET /api/files/download` - Example file download

## 📂 Project Structure

```
nexus/
├── src/clj/nexus/              # Backend source code
│   ├── core.clj                # Main entry point (-main function)
│   ├── system.clj              # Integrant system initialization & Aero config
│   ├── server.clj              # Ring/Jetty server, middleware stack
│   ├── db.clj                  # Database connection, HikariCP, query helpers
│   ├── errors.clj              # Custom error types
│   ├── service.clj             # Service protocol/pattern
│   │
│   ├── router/                 # Route definitions
│   │   ├── core.clj            # Main route composition
│   │   ├── api.clj             # API routes (/api/*)
│   │   └── web.clj             # Web routes (cookie-based)
│   │
│   ├── auth/                   # Authentication module
│   │   ├── jwt.clj             # JWT token generation/verification
│   │   ├── hashing.clj         # Password hashing (bcrypt)
│   │   ├── middleware.clj      # Auth middleware (header + cookie)
│   │   └── http_handlers.clj   # Auth HTTP handlers
│   │
│   ├── users/                  # User management module
│   │   ├── http_handlers.clj   # HTTP request handlers
│   │   ├── service.clj         # Business logic & validation
│   │   ├── queries.clj         # HoneySQL query builders
│   │   └── schemas/            # Malli schemas
│   │       ├── api.clj         # API request/response schemas
│   │       ├── domain.clj      # Service layer schemas
│   │       └── base.clj        # Shared base schemas
│   │
│   └── shared/                 # Shared utilities
│       ├── maps.clj            # Map transformation utilities
│       └── strings.clj         # String utilities
│
├── resources/
│   ├── system.config.edn       # Integrant component wiring
│   ├── envs/                   # Environment configurations
│   │   ├── dev.edn             # Development config
│   │   ├── prod.edn            # Production config
│   │   └── template.edn        # Config template
│   ├── migrations/             # Database migrations (Migratus)
│   │   ├── 20251014223555-add-nexus-schema.up.sql
│   │   └── 20251014223928-add-users-table.up.sql
│   └── public/                 # Static assets & compiled frontend
│       ├── index.html
│       └── assets/             # Vite build output
│
├── dev/nexus/                  # Development utilities
│   ├── user.clj                # REPL helpers (start, stop, restart)
│   ├── dev_system.clj          # Dev system access helpers
│   └── portal.clj              # Portal data visualization setup
│
├── test/clj/nexus/test/        # Test suite
│   ├── helpers.clj             # Test utilities
│   ├── test_system.clj         # Test system setup
│   ├── containers.clj          # Testcontainers configuration
│   └── users/                  # User module tests
│       ├── integration.clj     # End-to-end HTTP tests
│       ├── service_test.clj    # Service layer tests
│       ├── queries_test.clj    # Query tests
│       └── schemas_test.clj    # Schema validation tests
│
├── frontend/                   # React/TypeScript frontend
│   ├── src/
│   │   ├── main.tsx            # React app entry point
│   │   ├── routes/             # TanStack Router (file-based)
│   │   │   ├── __root.tsx      # Root layout
│   │   │   ├── index.tsx       # Homepage
│   │   │   ├── (guest)/        # Guest-only routes
│   │   │   │   ├── login.tsx
│   │   │   │   └── signup.tsx
│   │   │   └── (auth)/         # Protected routes
│   │   │       └── app/
│   │   │           └── index.tsx
│   │   ├── contexts/
│   │   │   └── auth.tsx        # Auth context (TanStack Query)
│   │   ├── lib/
│   │   │   ├── api.ts          # Backend API client
│   │   │   └── utils.ts        # Utility functions
│   │   └── components/         # React components
│   │       ├── Header.tsx
│   │       └── ui/             # Shadcn/ui components
│   ├── vite.config.ts          # Vite configuration
│   ├── tsconfig.json           # TypeScript configuration
│   └── package.json            # Frontend dependencies
│
├── project.clj                 # Leiningen project definition
├── docker-compose.yml          # PostgreSQL container
└── README.md                   # This file
```

### Module Organization Pattern

Each feature module (e.g., `users/`) follows this structure:

```
module/
├── http_handlers.clj    # HTTP layer - request/response handling
├── service.clj          # Service layer - business logic & validation
├── queries.clj          # Query layer - HoneySQL query builders
└── schemas/
    ├── api.clj          # API schemas (external interface)
    ├── domain.clj       # Domain schemas (internal logic)
    └── base.clj         # Shared schemas
```

This separation ensures:

- **Clear boundaries** between layers
- **Independent testing** of each layer
- **Schema reuse** across different contexts
- **Easy navigation** and maintenance

## 🗄️ Database

### Schema

The application uses a `nexus` schema in PostgreSQL:

```sql
-- nexus.users table
CREATE TABLE nexus.users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    middle_name VARCHAR(255),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),  -- Auto-updated via trigger
    deleted_at TIMESTAMP                          -- Soft delete
);

CREATE INDEX idx_nexus_users_email ON nexus.users (email);
```

### Migrations

Migrations are managed by **Migratus** and stored in `resources/migrations/`:

```bash
# Run all pending migrations
lein run migrate

# Rollback last migration
lein run rollback
```

Each migration has an `.up.sql` and `.down.sql` file for forward and backward changes.

### Connection Management

- **HikariCP** connection pooling for optimal performance
- Automatic **snake_case ↔ kebab-case** conversion
- Qualified keywords (`:users/email`) for clarity and prevent name collisions in queries using multiple tables
- Configurable via `resources/envs/{dev,prod}.edn`

## 🧪 Testing

### Running Tests

```bash
# Run all tests
lein test

# Run specific namespace
lein test nexus.test.users.integration

# Run with auto-reload (requires test-refresh plugin)
lein test-refresh
```

### Test Architecture

The test suite includes:

1. **Unit Tests** - Test individual functions in isolation
2. **Service Tests** - Test business logic with
3. **Query Tests** - Test HoneySQL query generation
4. **Schema Tests** - Test Malli schema validation
5. **Integration Tests** - End-to-end HTTP tests with real database and server

### Integration Testing with Testcontainers

Integration tests use **Testcontainers** to spin up a real PostgreSQL instance:

```clojure
(deftest register-user-test
  (test-system/with-system+server
    (fn [system]
      (let [server (:nexus.server/server system)
            response (http-request :post
                                   (str (server->host server) "/api/users/register")
                                   {:body test-user-data})]
        (is (= 201 (:status response)))))))
```

**Requirements**: Docker must be running for integration tests.

## 🚢 Production Deployment

### Building for Production

Clojure

```bash
# Build frontend and uberjar
./build.sh

# The standalone JAR is created at:
# target/uberjar/nexus-0.1.0-SNAPSHOT-standalone.jar
```

### Running in Production

```bash
# Set environment variables
export DB_URL="postgres://user:pass@host:5432/nexus"
export JWT_SECRET="your-secure-secret-key-here"
export PORT=8080

# Run the JAR
java -jar target/uberjar/nexus-0.1.0-SNAPSHOT-standalone.jar
```

### Environment Variables

| Variable     | Description                  | Example                                     |
| ------------ | ---------------------------- | ------------------------------------------- |
| `DB_URL`     | PostgreSQL connection string | `postgres://user:pass@localhost:5432/nexus` |
| `JWT_SECRET` | Secret key for JWT signing   | `your-secret-key-minimum-32-chars`          |
| `PORT`       | HTTP server port             | `8080`                                      |

## 🛠️ Development Tools

### REPL-Driven Development

The REPL is your primary development interface:

```clojure
;; In dev/nexus/user.clj

;; Start the system
(start)

;; Access components
(connection)      ; Get DB connection
(services:jwt)    ; Get JWT service
(server:router)   ; Get Reitit router
(server:handler)  ; Get Ring handler

;; Test functions directly
(require '[nexus.users.service :as users])
(users/list-users {:db (connection)} {:offset 0 :limit 10})

;; Restart after changes
(restart)

;; Stop the system
(stop)
```

### Portal - Data Visualization

**Portal** is integrated for data inspection:

```clojure
;; Send data to Portal
(tap> {:user/id 123 :user/email "test@example.com"})

;; Portal window opens automatically in dev mode
```

### Hot Reloading

In development mode:

- **Routes** are recompiled on every request
- **Handlers** can be changed without restart
- **Schemas** require restart to take effect
- **Database queries** can be changed without restart

### Debugging Tips

```clojure
;; Log with Telemere
(require '[taoensso.telemere :as tel])
(tel/log! {:level :info :data {:user-id 123}} "User logged in")

;; Inspect request/response in handlers
(defn my-handler [request]
  (tap> {:request request})  ; Send to Portal
  (let [response {:status 200 :body {:message "ok"}}]
    (tap> {:response response})
    response))

;; Test routes directly
((server:handler) {:request-method :get
                   :uri "/api/health"})
```

## 📚 Tech Stack Deep Dive

### Backend Technologies

| Technology     | Purpose             | Why This Choice                                                |
| -------------- | ------------------- | -------------------------------------------------------------- |
| **Integrant**  | Component lifecycle | Dependency injection, graceful startup/shutdown, testability   |
| **Aero**       | Configuration       | Environment-based config, EDN format, extensible readers       |
| **Reitit**     | Routing             | Data-driven, fast, great Malli integration, OpenAPI generation |
| **Malli**      | Schema validation   | Fast, composable, great error messages, generates docs         |
| **Ring/Jetty** | HTTP server         | Standard Clojure web stack, battle-tested, middleware-based    |
| **next.jdbc**  | Database access     | Modern, fast, simple API, connection pooling support           |
| **HoneySQL**   | SQL generation      | Composable queries, SQL as data, prevents injection            |
| **HikariCP**   | Connection pooling  | Fast, reliable, industry standard                              |
| **Migratus**   | Database migrations | Simple, version-controlled schema changes                      |
| **Buddy**      | Security            | JWT, password hashing, well-maintained                         |
| **Telemere**   | Logging             | Modern structured logging, great performance                   |

### Frontend Technologies

| Technology          | Purpose      | Why This Choice                                           |
| ------------------- | ------------ | --------------------------------------------------------- |
| **React 18**        | UI framework | Industry standard, great ecosystem, concurrent features   |
| **TypeScript**      | Type safety  | Catch errors early, better IDE support, self-documenting  |
| **TanStack Router** | Routing      | Type-safe, file-based, modern patterns, great DX          |
| **TanStack Query**  | Server state | Caching, synchronization, automatic refetching, mutations |
| **Axios**           | HTTP client  | Promise-based, interceptors, automatic JSON handling      |
| **Vite**            | Build tool   | Fast HMR, optimized builds, modern ESM support            |
| **Tailwind CSS**    | Styling      | Utility-first, fast development, consistent design        |
| **Shadcn/ui**       | Components   | Accessible, customizable, copy-paste approach             |

## 🎓 Learning Resources

### Understanding the Codebase

Start your exploration here:

1. **System Initialization**

   - Read `src/clj/nexus/system.clj` - Understand Integrant setup
   - Check `resources/system.config.edn` - See component wiring
   - Look at `src/clj/nexus/core.clj` - Main entry point

2. **Request Flow**

   - Follow a request through `src/clj/nexus/server.clj`
   - See middleware stack in `global-middlewares`
   - Trace route matching in `src/clj/nexus/router/`

3. **User Module** (complete example)

   - Start with `src/clj/nexus/users/http_handlers.clj`
   - Follow to `src/clj/nexus/users/service.clj`
   - See queries in `src/clj/nexus/users/queries.clj`
   - Check schemas in `src/clj/nexus/users/schemas/`

4. **Authentication**

   - JWT implementation in `src/clj/nexus/auth/jwt.clj`
   - Middleware in `src/clj/nexus/auth/middleware.clj`
   - See both auth modes (Bearer + Cookie)

5. **Frontend Integration**
   - API client in `frontend/src/lib/api.ts`
   - Auth hooks in `frontend/src/lib/auth.ts`
   - Route guards in `frontend/src/routes/`

### Key Concepts Demonstrated

- ✅ **Component-based architecture** with Integrant
- ✅ **Data-driven routing** with Reitit
- ✅ **Schema-first development** with Malli
- ✅ **Layered architecture** (HTTP → Service → Query → DB)
- ✅ **REPL-driven development** workflow
- ✅ **Integration testing** with Testcontainers
- ✅ **Dual authentication** (JWT + Cookies)
- ✅ **Type-safe frontend** with TypeScript
- ✅ **Modern React patterns** (hooks, TanStack)
- ✅ **Production-ready practices** (migrations, logging, error handling)

### Recommended Reading

**Clojure Web Development:**

- [Reitit documentation](https://cljdoc.org/d/metosin/reitit/)
- [Malli documentation](https://github.com/metosin/malli)
- [Integrant documentation](https://github.com/weavejester/integrant)
- [next.jdbc documentation](https://github.com/seancorfield/next-jdbc)

**Frontend:**

- [TanStack Router Docs](https://tanstack.com/router/latest)
- [TanStack Query Guide](https://tanstack.com/query/latest)
- [React TypeScript Cheatsheet](https://react-typescript-cheatsheet.netlify.app/)

## 🤝 Contributing

This is a learning project, but contributions are welcome! Areas for enhancement:

- [ ] Add rate limiting middleware
- [ ] Implement refresh tokens
- [ ] Add email verification flow
- [ ] Create admin dashboard
- [ ] Add more example modules (posts, comments, etc.)
- [ ] Implement WebSocket support
- [ ] Add GraphQL endpoint
- [ ] Create CLI tools
- [ ] Add metrics/monitoring (Prometheus)
- [ ] Implement caching layer (Redis)

## 🐛 Troubleshooting

### Common Issues

**Port already in use:**

```bash
# Find process using port 3456
lsof -i :3456
# Kill it
kill -9 <PID>
```

**Database connection failed:**

```bash
# Check if PostgreSQL is running
docker ps
# Restart if needed
docker compose restart
```

**Tests failing:**

```bash
# Ensure Docker is running (for Testcontainers)
docker info
# Clean and rebuild
lein clean
lein test
```

**Frontend not loading:**

```bash
# Rebuild frontend
# Build frontend and uberjar (includes frontend build)
./build.sh
```

**REPL not starting:**

```bash
# Clear target directory
lein clean
# Try again
lein repl
```

## 📝 License

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
