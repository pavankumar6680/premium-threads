# BuildMyBundle

**Goal-based e-commerce.** Instead of searching product by product, customers describe what they want to
accomplish ("set up a professional home office for $1,200") and BuildMyBundle assembles a complete,
compatible bundle from the real catalog, optimized for their budget, which they can customize and buy in a
single checkout.

> Status: **complete (phases 1–8)**: store, goal-based shopping, the recommendation engine, the bundle
> workspace, optional local AI (Ollama), the store admin, end-to-end tests, CI and a production-style Docker
> deployment. See [Roadmap](#roadmap) and [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## Features

- **Optional local AI (Ollama + Spring AI)**:
  - **Off by default**: everything works without it.
  - **Reading goals**: when on, the model reads free-text goals alongside the rule-based interpreter. Its answer is validated: only real categories, brands, tags and templates are accepted, and a budget only if the number appears in your text. It can add clarifying questions.
  - **"Explain with AI"**: writes a plain-language overview of your bundles from the engine’s facts. An answer that mentions a number not in those facts is discarded.
  - **Never decides**: AI never chooses products, prices or compatibility; the optimizer does.
- **Bundle workspace**:
  - **Customize** any recommended bundle into your own draft. Recommended bundles stay read-only.
  - **Swap** any item: every alternative shows its price difference and whether it is compatible with the
    rest of your bundle.
  - **Lock** favorites, then **re-optimize** the unlocked items. Choose the strategy (lowest cost / best
    value / best quality) and a new budget.
  - **Add or remove**: add compatible extras (rule-linked accessories and categories from your goal's
    template), remove nice-to-haves.
  - **Live totals and compatibility** after every edit. Incompatible bundles can't be bought as a bundle
    until the conflict is fixed.
  - **Save** under a name; your saved bundles appear under "My bundles".
- **Recommendation engine**:
  - **Three bundles per goal**: Budget, Balanced and Premium, chosen from every compatible combination of
    in-stock products. Identical tiers are merged, with an explanation rather than padding.
  - **Compatibility rules stored as data**: 18 rules (monitor ↔ PC ports, monitor ↔ arm load, size and VESA,
    CPU socket, lens mount, card slot, tripod and mic-arm load, "XLR mic needs an interface",
    smart-home ecosystems, desk and cardio footprint vs. your room). They run on structured product
    specs, never AI guesses.
  - **Exact optimization**: branch and bound with dominance pruning, verified against brute force on 400
    random problems. Real goals take a few milliseconds.
  - **Explained picks**: every pick has an explanation built from facts (price rank, rating, purpose,
    preferred brand, the compatibility checks it passes).
  - **Honest failures**: if nothing fits, you get the cheapest compatible cost and which item to drop,
    or the exact conflict.
  - **Add to cart**: a whole bundle goes into the cart in one step (all or nothing).
- **Goal-based shopping**:
  - **Templates**: 7 goal templates (home office, gaming, home gym, photo studio, study space, podcast
    studio, smart home), each with required and optional categories.
  - **Describe it in your own words**: a rule-based interpreter turns text into a pre-filled goal. For
    example, *"home office for $1,200, I need a desk, chair, monitor… I already have a webcam"* becomes
    the home-office template, a $1,200 budget, the categories you named and "already owned: webcam".
    It needs no AI service; the local LLM in Phase 6 will sit on top of it.
  - **4-step wizard**: mark each item need / nice to have / already own / skip, set the budget and approach
    (Budget / Balanced / Premium), then usage, preferred brands and room size.
  - **Live budget check**: the cheapest in-stock version of your must-haves is compared with your budget.
  - **Saved goals**: a goal can't be edited once saved ("Adjust" creates a new one) and has a shareable
    link, so it works for anonymous visitors too. Signed-in users see "My goals".
- **Catalog**: 6 departments, 33 categories and 142 seeded products with typed specifications. Prefix
  full-text search ("monit" finds monitors), and filters by category, brand, price, rating and any
  specification (e.g. *Inputs: USB-C*), with facet counts. Sorting and pagination are included.
- **Cart and wishlist**: the cart stores quantities only, so prices and stock are always live. Lines that
  can no longer be fulfilled are flagged before checkout.
- **Checkout**:
  - **Totals**: computed on the server by one pricing policy (free shipping over $100, simulated 8% tax).
  - **Order snapshot**: names, SKUs, prices and the shipping address are copied onto the order.
  - **No overselling**: stock is reserved with an atomic conditional `UPDATE`.
  - **No duplicate orders**: an `Idempotency-Key` makes retries and double clicks safe.
  - **All-or-nothing**: any failure rolls back the whole transaction.
- **Orders**: history, details, and cancellation before shipping, which restocks items and refunds the
  simulated payment exactly once (row-locked).
- **Addresses**: an address book with one enforced default per user.

- **Store admin** (`/admin`, ADMIN role):
  - **Dashboard**: revenue with trend, daily revenue chart, orders by status, best sellers, low stock,
    shopping goals by template, and recommendation-engine health (runs, "no bundle possible" share,
    p95 time).
  - **Products**: create and edit products and their specifications (validated against the category's
    attribute definitions), adjust stock inline, take products off sale. Concurrent edits are detected
    (optimistic locking).
  - **Orders**: ship, mark delivered, or cancel with restock and refund.
  - **Categories**: add categories and specifications without code changes.
  - **Compatibility rules**: create and edit rules, and preview how many product pairs a rule would
    block before saving it.
  - **Goal templates**: items, usage options and interpreter keywords.
  - **Activity log**: every admin change, who made it and when.
- **Rate limiting**: per user (or per IP when anonymous) with Bucket4j. Sign-in, AI calls, recommendation
  runs and checkout have tighter limits; a limited request gets `429` with `Retry-After`.

All brands and products are fictional. Product photos are not included, so each category has an
illustrated tile; real images can be attached through `product_images`.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Security 7, Spring Session JDBC, Spring Data JPA / Hibernate, Flyway, Spring Modulith, springdoc-openapi |
| Database | PostgreSQL 17 |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS 4, React Router, TanStack Query, React Hook Form, Zod |
| Testing | JUnit 5, Mockito, MockMvc, embedded PostgreSQL · Vitest, Testing Library, MSW |
| Ops | Docker, Docker Compose, nginx |

Architecture, data model, API contracts and the recommendation algorithm are described in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Getting started

### Prerequisites

- **JDK 21** (e.g. [Eclipse Temurin](https://adoptium.net/))
- **Node.js 22.12+** (24 LTS recommended)
- **Docker Desktop**, optional: only needed for the Compose workflows. The backend can run and be tested
  without Docker.

### Option A: local development, no Docker

```bash
# 1. Backend on http://localhost:8080 with an embedded PostgreSQL (data kept in backend/.data/)
cd backend
./mvnw spring-boot:test-run -Dspring-boot.run.main-class=com.buildmybundle.LocalDevApplication

# 2. Frontend on http://localhost:5173 (proxies /api to the backend)
cd frontend
npm install
npm run dev
```

The `dev` profile creates an administrator: **admin@buildmybundle.local / Admin12345**. The demo catalog
is loaded by Flyway on first start. To deploy an empty store, set
`FLYWAY_LOCATIONS=classpath:db/migration`.

### Optional: turn on local AI

Install [Ollama](https://ollama.com) and run `ollama pull llama3.2:3b`, then start the backend with
`AI_ENABLED=true` (PowerShell: `$env:AI_ENABLED="true"`). The goal page then shows **AI assist on**, and the
bundles page offers **Explain with AI**. Use `AI_MODEL` to pick another model and `OLLAMA_BASE_URL` for another host.

### Option B: local development with Docker PostgreSQL

```bash
cp .env.example .env                 # set DB_PASSWORD (and the admin account)
docker compose up -d postgres        # PostgreSQL on 127.0.0.1:5432
cd backend && DB_PASSWORD=<same as .env> ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm run dev
```

### Option C: full stack in containers

```bash
cp .env.example .env        # set DB_PASSWORD, ADMIN_EMAIL and ADMIN_PASSWORD
docker compose up -d --build
docker compose ps           # wait for "healthy", then open http://localhost:3000
```

Non-root, health-checked containers; nginx serves the app with security headers and proxies the API.
HTTPS, backups, upgrades and scaling are covered in **[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)**.

## API documentation

With the backend running: **Swagger UI** at http://localhost:8080/swagger-ui.html and the OpenAPI spec at
`/v3/api-docs`. Swagger is disabled in the `prod` profile unless `SWAGGER_ENABLED=true`.

All endpoints live under `/api/v1`. Errors are `application/problem+json`:

```json
{ "status": 400, "title": "Bad Request", "detail": "One or more fields are invalid.",
  "code": "VALIDATION_FAILED", "fieldErrors": [{ "field": "email", "message": "must be a well-formed email address" }] }
```

Authentication uses a session cookie. Clients call `GET /api/v1/auth/csrf` once, then send the `XSRF-TOKEN`
cookie value in an `X-XSRF-TOKEN` header on every `POST/PUT/PATCH/DELETE`.

## Testing

```bash
cd backend && ./mvnw test      # unit + integration tests on a real (embedded) PostgreSQL + module boundary check
cd frontend && npm test        # component and API-client tests (Vitest + MSW)
cd frontend && npm run e2e     # end-to-end purchase + fulfilment in a real browser (Playwright, installed Edge)
cd frontend && npm run build   # type-check + production build
```

CI ([.github/workflows/ci.yml](.github/workflows/ci.yml)) runs all of the above plus the Docker image builds on
every push and pull request.

## Project structure

```
buildmybundle/
├── backend/                      Spring Boot modular monolith
│   ├── src/main/java/com/buildmybundle/
│   │   ├── shared/               error model (problem+json), paging, OpenAPI config
│   │   ├── auth/                 users, roles, session security, /auth endpoints
│   │   ├── catalog/              categories, products, search + facets (SQL read model)
│   │   ├── inventory/            stock levels, atomic reservation
│   │   ├── pricing/              shipping / tax / totals policy
│   │   ├── account/              address book
│   │   ├── cart/  wishlist/      shopping cart, saved products
│   │   ├── payments/             simulated payment provider
│   │   ├── goals/                goal templates, rule-based goal interpreter, shopping goals
│   │   ├── compatibility/        compatibility rules (data) and the rule checker
│   │   ├── recommendation/       branch-and-bound optimizer, tiers, explanations, runs
│   │   ├── bundles/              recommended and customer bundles, workspace operations
│   │   ├── ai/                   optional language model (Spring AI + Ollama) with timeouts and status
│   │   ├── orders/               checkout, order history, cancellation, fulfilment
│   │   └── analytics/            admin dashboard reporting (read-only)
│   ├── src/main/resources/db/migration/   Flyway schema migrations
│   ├── src/main/resources/db/seed/        demo catalog (optional in deployments)
│   └── src/test/java/…           integration tests, LocalDevApplication (embedded Postgres)
├── frontend/                     React SPA
│   └── src/
│       ├── app/                  routes and layout
│       ├── components/ui/        design-system primitives
│       ├── features/             auth, catalog, cart, checkout, orders, wishlist, account, goals, recommendations, bundles, ai, admin
│       ├── lib/                  API client (CSRF + problem details), query client
│       └── test/                 MSW server and render helpers
│   └── e2e/                      Playwright end-to-end tests
├── docs/                         ARCHITECTURE.md, DEPLOYMENT.md
├── scripts/                      database backup and restore
├── .github/workflows/ci.yml      tests, E2E and image builds
└── docker-compose.yml            production-style stack
```

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 1. Foundation | Project setup, auth, sessions, migrations, app shell, Docker | ✅ Done |
| 2. E-commerce core | Catalog, categories, product pages, cart, wishlist, inventory, checkout simulation, orders | ✅ Done |
| 3. Goal-based shopping | Goals, requirement wizard, goal templates, category mapping | ✅ Done |
| 4. Recommendations | Branch-and-bound budget optimizer, compatibility engine, 3 tiers | ✅ Done |
| 5. Bundle builder | Replace / lock / remove items, live totals, saved bundles | ✅ Done |
| 6. AI features | Ollama via Spring AI with deterministic fallback | ✅ Done |
| 7. Admin and testing | Admin dashboard, security and E2E tests | ✅ Done |
| 8. Deployment | Production configuration, CI, backups and final docs | ✅ Done |
