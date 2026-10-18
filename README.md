# BuildMyBundle

**A premium multi-brand fashion marketplace that builds complete outfits.** Shop clothing and accessories
from 15 brands, or tell BuildMyBundle the occasion, your budget, sizes and favorite colors ("a stylish casual
outfit for a weekend party under $200, size M") and it puts together a complete look from different brands
that works together. Every piece comes in your size and in stock, and the whole outfit goes into the cart in one step.

> Status: **complete**. The original goal-based store (phases 1–8) was converted into a fashion marketplace
> in six further phases (F1–F6): catalog and variants, the storefront design, Build Your Outfit, outfit-aware
> AI, the fashion admin, and tests, docs and deployment. See [Roadmap](#roadmap).

> **Demo data.** Brand names (Nike, Adidas, Puma, Zara, H&M, Levi's, Tommy Hilfiger, Calvin Klein, Ralph Lauren,
> Lacoste, Uniqlo, Guess, Mango, Under Armour, Michael Kors) are shown as plain text for illustration only.
> Products, prices and stock are mock data. The store is not affiliated with or endorsed by these brands, and
> it uses no brand logos or product photos from them. Product pictures are generated garment illustrations in
> the variant's color. Homepage editorial photos are credited Unsplash images
> ([CREDITS](frontend/public/images/editorial/CREDITS.md)).

## Features

- **Storefront**:
  - **Premium design**: warm ivory, charcoal, coral and violet. Manrope and Playfair Display type, white cards.
  - **Homepage**: hero; Shop Men / Women / Accessories; featured brands; a trending carousel; new arrivals;
    shop by style (Casual, Streetwear, Formal, Minimalist, Party Wear); special offers; Build Your Outfit;
    recommended products. Admins manage the product rails.
  - **Catalog**:
    - Departments and categories: Men (T-shirts, shirts, jeans, trousers, jackets, hoodies, sweaters, ethnic
      wear), Women (dresses, tops, jeans, trousers, skirts, jackets, hoodies, ethnic wear) and Accessories
      (watches, sunglasses, handbags, backpacks, wallets, belts, jewelry, caps, scarves).
    - 126 mock products with 1,413 size × color variants.
    - Filters for brand, size, color, style, price, rating and offers (sale, new, trending). Prefix search.
  - **Brand pages**: every brand has a page and is a filter.
  - **Product cards**: picture in the selected color, brand, name, price with the original price, color
    swatches, sizes, wishlist, Quick View and add to cart.
  - **Product page**: gallery, color and size selection with per-variant stock, size guide, description,
    material and care, related products and “complete the look” matching pieces.
- **Build Your Outfit** (`/outfit`):
  - **Choices**: who it is for (men / women), the occasion (weekend, office, party, date night, festive,
    streetwear, travel, workout), the pieces (need / nice to have / skip), budget and priority, letter and
    waist sizes, up to 6 colors, styles and favorite brands. A sentence can fill in the choices instead.
  - **Three outfits per request**: Budget, Balanced and Premium, chosen by an exact budget optimizer over
    every combination of products that are in stock in your size.
  - **Fashion matching rules (stored as data)**: one wardrobe per outfit, dress codes at most two levels
    apart, and no two pieces competing for the same spot (no dress plus a top). They apply to every piece.
  - **Coordinated colors**: your colors come first. Otherwise neutrals, denim and metals form the base, with
    at most one accent color. Every outfit gets a palette note, and every piece says which size and color was
    picked and why.
  - **Workspace**: customize an outfit, swap pieces (with price difference and fit check), change size or
    color per piece, lock favorites and re-optimize, add or remove accessories, live totals, save it, and
    add the whole outfit to the cart.
  - **Show different pieces**: another combination for the same budget that avoids the previous pieces.
- **Optional local AI (Ollama)**:
  - **Off by default**: everything works without it.
  - **Reading requests**: the model's reading of free text is validated against the catalog. Wardrobe, sizes
    and colors always come from the deterministic reading of your own words, and a budget is accepted only
    if the number appears in your text.
  - **“Explain with AI”**: a stylist-style overview of why the pieces go together. It uses only the run's
    facts. Answers that name a brand or number that is not in the outfit are discarded.
  - **Never decides**: AI never invents brands, products, prices, sizes or availability. The optimizer and
    the catalog decide.
- **Cart, checkout and orders**:
  - **Cart**: lines are per variant (size and color), with live prices and stock.
  - **Checkout**: server-side totals, no overselling (atomic stock reservation per variant), idempotent order
    creation and all-or-nothing transactions.
  - **Orders**: snapshots of names, SKUs, sizes, colors and prices. Cancellation before shipping restocks
    the exact variant and refunds the simulated payment.
- **Store admin** (`/admin`, ADMIN role), in the same design system:
  - **Dashboard**: revenue and trend, orders, best sellers, low stock, outfits by occasion, engine health.
  - **Products**:
    - Brand, wardrobe, price and original price (discounts), featured / new / trending flags, material,
      care, styles and occasions.
    - A size × color variant editor with per-variant stock.
    - Image upload (JPEG, PNG or WebP up to 5 MB, checked by content). Optimistic locking on edits.
  - **Brands**: add, rename, feature on the homepage or retire, without code changes.
  - **Categories**, **styles & occasions**, and **homepage** rails (built-in and extra collections).
  - **Outfit rules**: create rules for specific categories or “any piece”, with a preview of how many product
    pairs a rule would block.
  - **Outfit templates**, **orders** (ship, deliver, cancel with refund) and the **activity log**.
- **Rate limiting** per user or IP (Bucket4j), with tighter limits for sign-in, AI, recommendations and checkout.

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

The `dev` profile creates an administrator: **admin@buildmybundle.local / Admin12345**. The demo fashion
catalog is loaded by Flyway on first start. Uploaded product images are stored in `backend/.data/media`
(`MEDIA_DIR`). To deploy an empty store, set
`FLYWAY_LOCATIONS=classpath:db/migration`.

### Optional: turn on local AI

Install [Ollama](https://ollama.com) and run `ollama pull llama3.2:3b`, then start the backend with
`AI_ENABLED=true` (PowerShell: `$env:AI_ENABLED="true"`). The outfit builder then shows **AI assist on**, and
the results page offers **Explain with AI**. Use `AI_MODEL` to pick another model and `OLLAMA_BASE_URL` for another host.

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
cd frontend && npm run e2e     # build an outfit in your size, buy it, ship it (Playwright, installed Edge)
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
│   │   ├── catalog/              brands, categories, products, variants, search + facets, admin, media
│   │   ├── inventory/            per-variant stock, atomic reservation
│   │   ├── pricing/              shipping / tax / totals policy
│   │   ├── account/              address book
│   │   ├── cart/  wishlist/      shopping cart, saved products
│   │   ├── payments/             simulated payment provider
│   │   ├── goals/                outfit templates, request interpreter, goals with the wearer's fit
│   │   ├── compatibility/        outfit rules (data) and the rule checker
│   │   ├── recommendation/       branch-and-bound optimizer, tiers, explanations, runs
│   │   ├── bundles/              outfits (bundles), size and color choice, workspace operations
│   │   ├── ai/                   optional language model (Spring AI + Ollama) with timeouts and status
│   │   ├── orders/               checkout, order history, cancellation, fulfilment
│   │   └── analytics/            admin dashboard reporting (read-only)
│   ├── src/main/resources/db/migration/   Flyway schema migrations
│   ├── src/main/resources/db/seed/        demo fashion catalog, outfit templates and rules (optional)
│   └── src/test/java/…           integration tests, LocalDevApplication (embedded Postgres)
├── frontend/                     React SPA
│   └── src/
│       ├── app/                  routes and layout
│       ├── components/ui/        design-system primitives
│       ├── features/             catalog, outfit, cart, checkout, orders, wishlist, account, goals, recommendations, bundles, ai, admin
│       ├── lib/                  API client (CSRF + problem details), query client
│       └── test/                 MSW server and render helpers
│   └── e2e/                      Playwright end-to-end tests
├── docs/                         ARCHITECTURE.md, DEPLOYMENT.md
├── scripts/                      backup and restore (database and uploaded images)
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
| F1. Fashion catalog | 15 brands, fashion categories, size × color variants with per-variant stock; users and orders kept | ✅ Done |
| F2. Storefront design | Premium theme, homepage, product cards, Quick View, product and brand pages | ✅ Done |
| F3. Build Your Outfit | Fit (wardrobe, sizes, colors), outfit rules, size and color per piece, outfit to cart | ✅ Done |
| F4. Outfit AI | Outfit-aware prompts, brand grounding, “show different pieces” | ✅ Done |
| F5. Fashion admin | Brands, variants, images, merchandising, styles and occasions, homepage rails, rules | ✅ Done |
| F6. Finish | Fashion tests and E2E, docs, Docker | ✅ Done |
