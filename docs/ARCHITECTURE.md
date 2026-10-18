# BuildMyBundle — Architecture

BuildMyBundle is a goal-based e-commerce platform: a customer describes an outcome ("a professional home
office for $1,200"), and the system assembles a complete, compatible, budget-optimized bundle from the real
catalog, which the customer can then customize and buy in one checkout.

## 1. System overview

```
┌──────────────────────────┐        ┌───────────────────────────────────────────────┐
│  React + TS (Vite)       │  HTTP  │  Spring Boot 4 modular monolith (Java 21)     │
│  TanStack Query, RHF+Zod │◄──────►│                                               │
│  Tailwind, React Router  │ /api/v1│  auth ─ catalog ─ inventory ─ goals           │
└──────────────────────────┘ session│  recommendation (engine) ─ bundles            │
     served by nginx /       cookie │  cart ─ orders ─ payments(sim) ─ admin        │
     Vite dev proxy          + CSRF │  ai (optional adapter) ─ shared               │
                                    └──────────┬───────────────────────┬────────────┘
                                               │ JPA / Flyway          │ Spring AI (HTTP)
                                         ┌─────▼──────┐          ┌─────▼──────┐
                                         │ PostgreSQL │          │  Ollama    │  optional
                                         └────────────┘          └────────────┘
```

### Key decisions

| Decision | Choice | Why |
|---|---|---|
| Architecture | Modular monolith, verified by Spring Modulith (`ModularityTest`) | Clear module boundaries without distributed-system cost; boundaries are enforced by a test, not convention. |
| Auth | Server-side session (Spring Session JDBC) in an `HttpOnly; SameSite=Lax` cookie + cookie-to-header CSRF | Same-origin SPA: safer than JWT in `localStorage`, real logout/revocation, sessions survive restarts. |
| API errors | RFC 9457 `application/problem+json` with stable `code` and `fieldErrors` | One error format for the frontend, including 401/403 raised inside the security filter chain. |
| Money | `NUMERIC(12,2)` / `BigDecimal`; optimizer uses `long` cents | No floating-point money; totals are always computed server-side. |
| AI | Optional adapter only | The engine is deterministic and works on DB data; AI converts text → validated requirements and rewords explanations from DB facts. |
| Schema | Flyway owns every table (incl. Spring Session) and Hibernate runs `ddl-auto=validate` | Reproducible, reviewable migrations. |

### Module layout (backend)

Each module is a top-level package under `com.buildmybundle`. Types directly in the module package are its
public API (e.g. `auth.AuthenticatedUser`); sub-packages (`domain`, `service`, `web`, `security`) are internal.
`shared` is an OPEN module (error model, config).

## 2. Scope

| MVP (Phases 1–5 + core admin + tests) | v1.1 (Phases 6–8) | Future |
|---|---|---|
| Auth, roles, addresses | Ollama goal parsing + clarifying questions | Multi-seller (`seller_id` reserved) |
| Catalog, search, filters, product detail | AI explanation rewording | Real payment provider |
| Cart, wishlist, simulated checkout, orders | Product comparison page | Collaborative / personalized recommendations |
| Inventory with atomic decrement | Admin analytics charts | Object storage for images |
| Goal templates + wizard (no AI needed) | Rate limiting on bundle generation (Bucket4j) | |
| Engine: 3 tiers, budget optimization, compatibility | Playwright E2E | |
| Bundle workspace: replace / lock / remove / recalc / save | | |
| Admin: products, attributes, inventory, rules, templates, orders | | |
| Seed catalog (~150 products across 7 goals) | | |

## 3. Data model

```
users(id, email UQ, password_hash, full_name, enabled, created_at, updated_at, version)
roles(id, name UQ)                user_roles(user_id FK, role_id FK, PK both)
addresses(id, user_id FK, line1, line2, city, state, postal_code, country, is_default)

categories(id, parent_id FK NULL, slug UQ, name)
products(id, category_id FK, sku UQ, name, brand, description, price NUMERIC(12,2),
         rating_avg, rating_count, quality_tier, status, seller_id NULL, version)
product_images(id, product_id FK, url, sort_order)
attribute_definitions(id, category_id FK, key, data_type[TEXT|NUMBER|BOOL|LIST], unit, UQ(category_id,key))
product_attributes(id, product_id FK, attribute_id FK, value_text, value_number, value_bool)
inventory(product_id PK/FK, quantity_on_hand CHECK >= 0, version)
compatibility_rules(id, source_category_id, target_category_id, operator, source_attr_key,
                    target_attr_key, severity[BLOCK|WARN], message, active)
product_tags(product_id, tag)

goal_templates(id, slug UQ, title, description, image_url, default_budget, active)
goal_template_items(id, template_id FK, category_id FK, required, weight, min_qty, max_qty)
shopping_goals(id, user_id NULL, template_id NULL, raw_text, budget, constraints JSONB,
               preferred_brands TEXT[], owned_category_ids BIGINT[], usage_tags TEXT[], created_at)
goal_requirements(id, goal_id FK, category_id FK, required, weight, locked_product_id NULL)
recommendation_runs(id, goal_id FK, algorithm_version, input_snapshot JSONB, status,
                    infeasible_reason, min_feasible_cost, created_at)
bundles(id, run_id NULL, user_id NULL, goal_id FK, tier[BUDGET|BALANCED|PREMIUM|CUSTOM], name,
        status[RECOMMENDED|CUSTOMIZED|SAVED], total_price, score, version)
bundle_items(id, bundle_id FK, product_id FK, category_id, quantity, locked, optional, explanation)

carts(id, user_id UQ)             cart_items(id, cart_id FK, product_id FK, qty CHECK > 0, bundle_id NULL)
wishlist_items(user_id, product_id, created_at, PK both)
orders(id, user_id FK, status, subtotal, shipping, tax, total, shipping_address JSONB,
       idempotency_key, created_at, UQ(user_id, idempotency_key))
order_items(id, order_id FK, product_id FK, product_name, sku, unit_price, qty, line_total)  -- price snapshot
payments(id, order_id UQ FK, amount, status, simulated_ref, created_at)
```

- **Typed EAV** for product attributes keeps the schema normalized and gives the rules engine typed values.
- **Oversell-proof checkout**: `UPDATE inventory SET quantity_on_hand = quantity_on_hand - :q WHERE product_id = :id AND quantity_on_hand >= :q`; zero rows updated rolls back the whole order transaction.
- **Idempotent order creation**: `Idempotency-Key` header + unique `(user_id, idempotency_key)`.

## 4. API (`/api/v1`)

| Area | Endpoints |
|---|---|
| Auth ✅ | `GET /auth/csrf`, `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/me` |
| Catalog ✅ | `GET /categories`, `GET /products?q&category&brand&minPrice&maxPrice&minRating&tag&attr.{key}=v&sort&page&size`, `GET /products/facets?q&category`, `GET /products/{slug}` |
| Goals ✅ | `GET /goal-templates`, `GET /goal-templates/{slug}`, `GET /goal-categories`, `POST /goals/analyze`, `POST /goals`, `GET /goals/{id}`, `GET /me/goals`, `GET /ai/status`, `POST /recommendations/{runId}/explanation` |
| Recommendations ✅ | `POST /goals/{id}/recommendations`, `GET /goals/{id}/recommendations/latest`, `GET /recommendations/{runId}`, `GET /bundles/{id}`, `POST /cart/bundles/{id}` |
| Bundle workspace ✅ | `GET /bundles/{id}`, `POST /bundles/{id}/customize`, `GET /bundles/{id}/alternatives?category`, `GET /bundles/{id}/accessories`, `PUT/PATCH/DELETE /bundles/{id}/items/{category}`, `POST /bundles/{id}/items`, `PUT /bundles/{id}/budget`, `POST /bundles/{id}/reoptimize?strategy`, `POST /bundles/{id}/save`, `DELETE /bundles/{id}`, `GET /me/bundles` |
| Shopping ✅ | `GET /cart`, `POST /cart/items`, `PUT/DELETE /cart/items/{productId}`, `DELETE /cart`, `GET /wishlist`, `PUT/DELETE /wishlist/{productId}`, `GET/POST /me/addresses`, `PUT/DELETE /me/addresses/{id}`, `POST /me/addresses/{id}/default`, `POST /checkout` (Idempotency-Key), `GET /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancel`. Phase 5 adds `POST /cart/bundles/{id}` |
| Admin | `/admin/products`, `/admin/categories`, `/admin/attributes`, `/admin/inventory`, `/admin/compatibility-rules`, `/admin/goal-templates`, `/admin/orders`, `/admin/analytics/summary` |

Pagination shape: `{content, page, size, totalElements}`. `/admin/**` requires `ROLE_ADMIN`.
Every bundle mutation returns the fully recalculated bundle (totals, budget usage, compatibility report).

## 5. Recommendation engine

**Problem**: a multiple-choice knapsack with pairwise constraints. Each required category is a group (pick
exactly one product), optional categories pick zero or one; maximize total utility subject to
`Σ price ≤ budget` and every pair of chosen products being compatible.

**Pipeline**

1. **Requirements**: categories, budget, brands, owned items (excluded, but their attributes still feed
   compatibility), usage tags, space constraints.
2. **Candidate filter**: in stock, active, unary rules (e.g. `desk.width_cm ≤ space.width_cm`).
3. **Utility**: `w_q·quality + w_r·tagRelevance + w_b·brandBonus`, with Bayesian-averaged ratings, scaled by
   the category weight.
4. **Pruning**: drop Pareto-dominated products per category (only among products with identical
   rule-relevant attributes, so no feasible combination is lost); cap at top-K (≈12).
5. **Branch and bound**: categories ordered by fewest candidates; prune when
   `cost + Σ minRemainingCost > budget` or `utility + Σ maxRemainingUtility ≤ best`; check compatibility
   incrementally; locked items are pre-assigned.
6. **Tiers**: Budget = minimize cost s.t. quality floor; Balanced = maximize utility − λ·cost;
   Premium = maximize quality within budget. If two tiers overlap > 70 %, re-solve excluding shared items;
   if no distinct valid bundle exists, say so instead of padding.
7. **Infeasible**: a min-cost search reports the minimum feasible cost and which optional items to drop.

**Complexity**: worst case `O(K^C)` time, `O(C)` stack for C categories and K candidates; with C ≤ 10,
K ≤ 12 and pruning, typical runs visit a few thousand nodes; a node cap and timeout bound the worst case.
A DP over budget (`O(C·K·B)`) solves the unconstrained case, but pairwise compatibility breaks its optimal
substructure, so branch and bound is the general solver.

**Compatibility rules** are data (`compatibility_rules`), evaluated by one strategy per operator:
`EQUALS` (CPU socket = motherboard socket), `LIST_INTERSECTS` (monitor inputs ∩ PC outputs ≠ ∅),
`NUMBER_LTE` (monitor weight ≤ arm max load), `LIST_CONTAINS` (arm VESA patterns ∋ monitor VESA),
`LTE_USER_CONSTRAINT` (desk width ≤ available space). Checkout re-validates bundles sold as compatible.

## 6. Security

- Passwords: `DelegatingPasswordEncoder` (bcrypt, upgradable via `{id}` prefix); 8–72 chars.
- Session: `BMB_SESSION`, `HttpOnly`, `SameSite=Lax`, `Secure` behind HTTPS (`COOKIE_SECURE`), id rotated on
  login (fixation protection), stored in PostgreSQL.
- CSRF: readable `XSRF-TOKEN` cookie echoed as `X-XSRF-TOKEN`; token rotated on login.
- Login errors never reveal whether an account exists.
- Admin bootstrap credentials come from environment variables, never from migrations.
- Prices and totals are computed server-side only; inventory checked transactionally at checkout.
- Rate limiting (Bucket4j token buckets, per user or per IP; sign-in always per IP): 10/min for sign-in and
  registration, 10/min for AI calls, 30/min for recommendation runs, 10/min for checkout, 300/min otherwise.
  Configurable through `RATE_LIMIT_*`. Buckets are in memory per instance (Redis would be the multi-node step).

## 7. Implementation notes (Phase 2)

- **Catalog reads are a SQL read model** (`ProductQueries`, `JdbcClient`). Search combines a stored
  `tsvector` column with a GIN index, prefix queries (`stand:* & desk:*`) built from sanitized terms, a
  recursive CTE for department scope and `EXISTS` predicates on the EAV table. Dynamic SQL uses only fixed
  fragments; every user value is a bind parameter.
- **Facets** are computed for the *scope* (category plus text query), not for the other refinements, so
  filter options do not disappear while the user is narrowing results. Attribute facets appear only for a
  single leaf category, and a facet with a single value is hidden.
- **One canonical value form** (`COALESCE(value_text, value_bool::text, trim_scale(value_number)::text)`) is
  shared by facets and filters, so a facet value always round-trips as a filter.
- **Module APIs**: other modules use `ProductCatalog`/`ProductSummary`, `InventoryService`, `PricingPolicy`,
  `AddressBook`, `CartService` and `PaymentService`; Spring Modulith's test rejects any access to
  internals.
- **Checkout** (`OrderService`):
  1. A replayed `Idempotency-Key` returns the original order.
  2. Otherwise one transaction snapshots the cart at live prices and computes the totals.
  3. It inserts the order, then flushes. A concurrent duplicate key fails here, before stock is touched.
  4. It reserves stock in product-id order (no deadlocks), charges the simulated payment and clears the cart.
  5. A losing concurrent duplicate returns the winning order.
- **Cancellation** locks the order row (`PESSIMISTIC_WRITE`), so restocking and refunding happen exactly once.
- **Carts** are created with `INSERT … ON CONFLICT DO NOTHING`, and an optimistic `@Version` on the cart
  turns concurrent edits into a 409 instead of lost updates.

## 8. Implementation notes (Phase 3)

- **Goals are immutable** and keyed by a random UUID. "Adjust goal" creates a new goal, so Phase 4
  recommendation runs always refer to exactly the inputs they were computed for. The UUID doubles as an
  unguessable share link, which lets anonymous visitors create and view goals; `user_id` is set when
  signed in.
- **Requirements** are per category with kind `REQUIRED`, `OPTIONAL` or `OWNED`. Owned categories are
  never bought, but they stay on the goal: the engine skips them, and Phase 4 can still use them as context.
- **Estimates use live data**: the cheapest active, in-stock product per category, times quantity, gives
  the essentials minimum and the selection minimum. This is a lower bound before compatibility rules
  apply. Template cards show it as "complete setups from $X".
- **Goal interpretation** (`GoalInterpreter`) is a pure function over a vocabulary loaded from the
  database:
  - The template with the highest keyword score wins.
  - Budgets are recognised as `$1,200`, `$1.5k`, `under 2k` or `800 dollars`. A bare `4k` is ignored,
    so it can't be mistaken for a budget.
  - Category phrases plus synonyms are matched longest-first; ambiguous words go to the matched
    template's category.
  - Clauses like "already have…" mark categories as owned.
  - Quality, usage tags and catalog brands come from fixed phrase lists.
  - The interpreter returns the matched terms (shown to the customer) and clarifying questions when the
    budget or items are missing.
  - Phase 6 adds an LLM interpreter that falls back to these rules.

## 9. Implementation notes (Phase 4)

**Modules**:
- `compatibility` owns the rules and a pure checker.
- `recommendation` owns the engine and the stored runs.
- `bundles` stores bundles and re-checks them on every read.

Dependencies run in one direction: recommendation → bundles → compatibility → catalog. Spring Modulith verifies there are no cycles.

**Rules** (`compatibility_rules`) are evaluated by one strategy per operator: `EQUALS`, `LIST_CONTAINS`, `LIST_INTERSECTS`, `LTE`, `LTE_SPACE_WIDTH/DEPTH` and `REQUIRES_CATEGORY_UNLESS`. A rule whose attribute is missing is treated as *not applicable*, not as violated: the store does not claim an incompatibility it has no data for.

**Scoring** (all components in [0, 1]):
- *Quality*: a Bayesian-averaged rating (prior 4.0 with the weight of 50 reviews), blended 60/40 with the product's quality tier.
- *Relevance*: half for the goal's purpose tag, half for the share of usage preferences matched.
- *Brand*: 1 for a preferred brand.

An option scores `needWeight × (q·Q + r·R + b·B) − penalty × cost / budget`. Optional categories count 0.6.

| Tier | q | r | b | Cost penalty | Buys optional items |
|---|---|---|---|---|---|
| Budget | 0.30 | 0.50 | 0.20 | 4.0 | No |
| Balanced | 0.45 | 0.40 | 0.15 | 1.2 | When worth it |
| Premium | 0.70 | 0.25 | 0.05 | 0 | When affordable |

The cost penalties were tuned on the seed catalog. With 0.6, Balanced spent 96% of the reference home-office budget and collapsed into Premium; with 1.2, the reference goal gives $594 / $944 / $1,154.

**Purpose filter**: in categories where some products carry the goal's purpose tag (e.g. `gaming`), only those are considered, so a gaming bundle never contains an office mini PC. If that makes a tier impossible, the tier is re-solved with all products, and its summary says so.

**Search**: `BundleOptimizer` (see its Javadoc):
- Candidates are filtered by stock, space and affordability.
- Dominance pruning is safe because it only compares products with identical rule-relevant attributes.
- At most 12 options are kept per category.
- Categories with the fewest options are searched first, with options in best-first order.
- Branches are cut by a cost bound and a score bound. Pair compatibility is checked incrementally (cached); presence rules are checked at the leaves.
- Limits: 300k nodes / 2 s. If either is hit, the result is flagged as incomplete.

Complexity is `O(K^C)` in the worst case; on the seed catalog, real goals explore 14–95 nodes in 1–24 ms.

**Correctness**: `BundleOptimizerTest` compares against exhaustive enumeration on 400 seeded random problems: the score must be optimal, the cost within budget, and every pair plus every presence rule compatible.

**Infeasibility**: when no tier is possible, `diagnose` reports one of three things, in this order:
1. A must-have category with nothing buyable (out of stock, or nothing fits the space).
2. The cheapest compatible cost, from a min-cost search without the budget, plus up to three "drop X: the rest starts at $Y" suggestions.
3. The pair of categories that has no compatible combination, or a missing required category.

**Persistence**: a run stores its inputs' outcome (status, minimum cost, statistics, messages) and its bundles. Bundle items keep the price at recommendation time, and views show live prices, availability and a fresh compatibility report.

## 10. Implementation notes (Phase 5)

- **Ownership**: `RECOMMENDED` bundles belong to a run and are immutable. `POST /customize` copies one into a `DRAFT` owned by the signed-in customer (`source_bundle_id` keeps the provenance), and `save` makes it `SAVED`.
  - Changing a recommended bundle returns 409 `BUNDLE_READ_ONLY`.
  - Someone else's draft looks like a 404.
  - `@Version` turns concurrent edits into 409s.
- **Edits are checked, not blocked**: a swap may make the bundle incompatible.
  - Every read re-runs the rules, and the view reports `compatible` and `purchasable`.
  - Adding the bundle to the cart is refused with `BUNDLE_INCOMPATIBLE`.
  - Customers can explore freely, but never buy an incompatible set as a bundle.
- **Slots**: there is one item per category (`UNIQUE (bundle_id, category_id)`). Swaps and re-optimization update a slot in place instead of delete + insert, which also avoids Hibernate's insert-before-delete flush order.
- **Alternatives**: each candidate in the slot's category is checked against the rest of the bundle (pair, space and presence rules). It shows the price delta at live prices; the current product is listed first, then compatible, in-stock and cheaper options.
- **Accessories** come from three sources, each with up to three compatible, in-stock, best-rated products:
  1. Categories linked by a rule to something in the bundle ("Works with your …").
  2. The goal's unselected categories.
  3. The template's other categories ("Popular for … setups").
- **Re-optimize** (`BundleOptimization`, in the recommendation module so dependencies stay one-directional) calls `RecommendationEngine.optimizeTier`:
  - It keeps the bundle's categories, fills every slot and fixes locked products.
  - The strategy is the bundle's tier unless the request picks another one. Without that choice, a Budget bundle given a bigger budget would stay cheap by design.
  - If nothing fits, it answers 409 `REOPTIMIZE_INFEASIBLE` with the cost of the locked items plus the cheapest compatible rest.

## 11. Implementation notes (Phase 6)

- **`ai` module**: `LanguageModel` is the only API.
  - **Implementation**: `OllamaLanguageModel` uses Spring AI's `ChatModel` and Ollama's JSON mode for structured answers.
  - **Availability**: probed through `/api/tags` (cached 30 s, and the model must be installed).
  - **Hard timeout**: every call runs on a virtual thread with a timeout (`AI_TIMEOUT`, default 60 s); Spring AI retries are off.
  - **Failures**: any failure or timeout returns empty, so callers fall back. It is off unless `AI_ENABLED=true`.
- **Goal interpretation**: `AssistedGoalInterpreter` runs the rules, then the model, and `AiGoalMerger` validates and merges. A valid store value is not enough: in live tests llama3.2:3b added real but unrequested categories, brands and a premium preference. So the model contributes only what is grounded in the shopper's text or fills a gap the rules left:
  - a template, only when the rules found none (e.g. "competitive shooters" → gaming setup);
  - a budget, only if that number appears in the text;
  - "already owned" for a category named in a sentence that is not a request ("my old monitor still works", but never "I need a desk…");
  - usage tags offered by the chosen template;
  - at most one question, only when the budget or the needed products are missing.

  It cannot add categories, brands or a quality preference. In the worst case, the result equals the rules-only result. The prompt lists slugs only and output is capped at 350 tokens; on a CPU-only laptop this cut analysis from about 50 s to 10–35 s.
- **Overviews**: `RunExplainer` sends the model only the run's facts: bundles, totals, budget shares, items, ratings, notes, or reasons, minimum cost and suggestions. `Grounding` rejects any answer containing a number that is not in the facts; small counts up to 10 are allowed. Accepted text is cached as an `AI_OVERVIEW` run message.
- **The engine never uses AI**: products, prices, compatibility and per-item explanations stay deterministic.
- **Tests**: the model is replaced by a scripted fake (`AiIntegrationTest`), so CI needs no Ollama.

## 12. Implementation notes (Phase 7)

- **Admin endpoints live in the module that owns the data**: catalog (`/admin/products`, `/admin/categories`),
  orders (`/admin/orders`), compatibility (`/admin/compatibility-rules`), goals (`/admin/goal-templates`).
  Cross-module needs go through public APIs: `UserDirectory` (auth) gives order screens the customer's name,
  `ProductCatalog.attributeKeysByCategory()` lets rule validation check attributes.
- **Product edits**: attribute values are validated against the category's definitions (type, single vs list
  values), because filters and compatibility rules read them. Updates carry the version they were based on;
  a stale version gets `409 STALE_PRODUCT` instead of silently overwriting another admin's change. Slugs are
  generated once and never change, so storefront links stay valid.
- **Order fulfilment**: PAID → SHIPPED → DELIVERED, and PAID → CANCELLED (staff cancel restocks and refunds
  exactly like a customer cancel). Transitions take a row lock.
- **Rule preview**: evaluates an unsaved rule against every active product pair it applies to and reports
  how many it would block, with examples, so a typo cannot silently empty a category.
- **Audit log**: `admin_audit_log` (actor, action, entity, JSON details) is written in the same transaction
  as the change.
- **Analytics** (`analytics` module): a read-only reporting model that aggregates over several modules'
  tables in SQL. This is the one deliberate exception to table ownership; it never writes, and no module
  depends on it.
- **Frontend**: the admin area is a lazily loaded chunk (shoppers never download it), and vendor libraries
  are split into separately cached chunks.
- **End-to-end test** (`frontend/e2e`, Playwright on the installed Edge): sign up, build a goal with the
  wizard, add the Balanced bundle to the cart, check out, then sign in as staff and ship the order.
  It found a real bug that jsdom could not: the wizard's Continue and Create buttons shared one DOM
  element, so the browser submitted the form on the last Continue and skipped the review step.

## 13. Testing strategy

- Backend unit tests (JUnit 5 + Mockito) for services, the engine and rule evaluators, with property-style
  tests (no result exceeds budget; every pair compatible) over randomized catalogs.
- Backend integration tests on a **real PostgreSQL** (embedded binaries, no Docker needed) through the full
  security filter chain, using a cookie-jar test client that performs the real CSRF flow.
- `ModularityTest` verifies module boundaries.
- Frontend: Vitest + Testing Library + MSW, rendering the real route tree.
- End-to-end: Playwright against the real stack (`npm run e2e`); it starts the backend and Vite if needed.
