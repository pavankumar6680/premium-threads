# Deploying Premium Threads

The production stack runs as three containers (four with optional local AI), described in
[`docker-compose.yml`](../docker-compose.yml):

| Service | Image | Role | Published |
|---|---|---|---|
| `frontend` | `nginx-unprivileged` + built SPA | Serves the app, proxies `/api` to the backend, sets security headers | `HTTP_PORT` (default 3000) |
| `backend` | Spring Boot on JRE 21 | REST API, sessions, migrations | `127.0.0.1:8080` only |
| `postgres` | `postgres:17-alpine` | All data, including sessions | `127.0.0.1:5432` only |
| `ollama` (profile `ai`) | `ollama/ollama` | Optional local language model | not published |

Everything the browser talks to goes through nginx on one origin, so the session cookie and CSRF token
work without cross-site configuration.

## 1. First start

Requirements: Docker Engine 24+ with Compose v2 (Docker Desktop on Windows or macOS), 2 CPUs and 2 GB RAM
(8 GB if you enable AI).

```bash
cp .env.example .env
# edit .env: set DB_PASSWORD, ADMIN_EMAIL and ADMIN_PASSWORD (Compose refuses to start without them)
docker compose up -d --build
docker compose ps        # wait until all services are "healthy" (the backend takes about 30 s)
```

Open http://localhost:3000 and sign in with the admin account from `.env`. On the first start Flyway creates
the schema and, unless `FLYWAY_LOCATIONS` says otherwise, loads the demo catalog. The admin account is
created only if it does not exist yet; changing `ADMIN_PASSWORD` later does not change an existing account.

## 2. Configuration

All settings are environment variables, read from `.env` by Compose.

| Variable | Default | Purpose |
|---|---|---|
| `DB_PASSWORD` | (required) | PostgreSQL password |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | (required) | Bootstrap administrator |
| `HTTP_PORT` | `3000` | Port of the web tier on the host |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Public origin of the site |
| `COOKIE_SECURE` | `false` | `true` behind HTTPS (session cookie gets `Secure`) |
| `SWAGGER_ENABLED` | `false` | Swagger UI at `http://localhost:8080/swagger-ui.html` |
| `FLYWAY_LOCATIONS` | migrations + demo seed | `classpath:db/migration` for an empty store |
| `DB_USERNAME`, `DB_POOL_SIZE` | `bmb`, `10` | Database user and connection pool size |
| `RATE_LIMIT_ENABLED` | `true` | Per-client request limits (`RATE_LIMIT_AUTH`, `_AI`, `_ENGINE`, `_CHECKOUT`, `_GENERAL` per minute) |
| `AI_ENABLED`, `AI_MODEL`, `AI_TIMEOUT` | `false`, `llama3.2:3b`, `60s` | Optional local AI |
| `APP_VERSION` | `local` | Tag for the built images |

## 3. HTTPS

Run the stack behind a TLS-terminating reverse proxy (Caddy, Traefik, a cloud load balancer) that forwards
to `HTTP_PORT`, then set:

```dotenv
COOKIE_SECURE=true
CORS_ALLOWED_ORIGINS=https://shop.example.com
```

The bundled nginx is the edge as shipped: it **overwrites** `X-Forwarded-For` with the connecting address so
clients cannot spoof the IP used for rate limiting. Behind another proxy, configure nginx's `realip` module
(`set_real_ip_from <proxy network>; real_ip_header X-Forwarded-For;`) so the original client address is kept,
and forward `X-Forwarded-Proto: https`.

A minimal Caddy setup (automatic certificates):

```caddyfile
shop.example.com {
    reverse_proxy localhost:3000
}
```

## 4. Local AI (optional)

```bash
# .env: AI_ENABLED=true
docker compose --profile ai up -d --build
docker compose exec ollama ollama pull llama3.2:3b     # once, about 2 GB
```

Everything works without AI; with it, goals written in free text are read better and recommendation pages
get a short overview. On a CPU-only host expect 10 to 35 seconds per AI call. The backend never waits longer
than `AI_TIMEOUT`, then falls back to its deterministic rules.

## 5. Operations

**Health.** Each container has a health check. The backend exposes `/actuator/health/liveness` and
`/actuator/health/readiness` (port 8080, localhost only); nginx answers `/healthz`.

**Logs.** The backend writes one JSON object per line (Elastic Common Schema) to stdout; Docker keeps 5 × 10 MB
per container. `docker compose logs -f backend` follows them.

**Backups.**

```bash
scripts/backup.sh       # -> backups/buildmybundle-<timestamp>.sql.gz and buildmybundle-<timestamp>-media.tar.gz
scripts/restore.sh backups/buildmybundle-<timestamp>.sql.gz [backups/buildmybundle-<timestamp>-media.tar.gz]
```

Product images uploaded in the admin live in the `media` volume (mounted at `/app/media`, served by nginx
under `/media`). The backup script archives them next to the database dump, and the restore script restores
them when you pass the archive.

Schedule `backup.sh` (cron or Task Scheduler) and copy the files off the host. On Windows, run the scripts from
Git Bash or WSL.

**Upgrades.** Pull the new version and run `docker compose up -d --build`. Flyway applies new migrations on
start-up (they are forward-only; take a backup first). The backend shuts down gracefully, finishing requests in
flight for up to 20 seconds. Sessions live in PostgreSQL, so users stay signed in across restarts.

**Scaling.** The backend is stateless apart from the database: several instances can share one PostgreSQL
(sessions are in the database). Two things are per instance and need attention before scaling out:

- rate-limit buckets are kept in memory: move them to Redis (Bucket4j has a Redis backend) or limit at the proxy;
- uploaded images are on a local volume: share it between instances or move images to object storage;
- the AI status cache is per instance, which is harmless.

## 6. Security checklist

- [ ] Strong `DB_PASSWORD` and `ADMIN_PASSWORD`; `.env` is not committed (it is git-ignored).
- [ ] HTTPS with `COOKIE_SECURE=true` and the public `CORS_ALLOWED_ORIGINS`.
- [ ] `SWAGGER_ENABLED=false` in production (the default).
- [ ] PostgreSQL and the API stay bound to `127.0.0.1` (or are removed from `ports`).
- [ ] Backups scheduled and a restore tested (database and the media archive).
- [ ] The demo catalog uses real brand names as mock data. Before any public deployment, replace it with your
      own catalog (`FLYWAY_LOCATIONS=classpath:db/migration` starts empty), or make sure you have the right
      to use the brand names.

What the stack already does: non-root containers with read-only file systems where possible and
`no-new-privileges`; security headers (Content-Security-Policy, X-Frame-Options, Referrer-Policy,
Permissions-Policy, nosniff); session cookie `HttpOnly` + `SameSite=Lax`; CSRF protection; bcrypt passwords;
server-side prices and totals; rate limiting; an audit log of admin changes; image uploads checked by file
content (JPEG, PNG or WebP, 5 MB) and stored under generated names.

## 7. Continuous integration

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) runs on every push to `main` and every pull request:
backend tests (embedded PostgreSQL), frontend tests and build, the Playwright end-to-end journey in Chromium,
and both Docker image builds. All of it runs on free GitHub-hosted runners with no secrets.

To check a running deployment end to end:

```bash
cd frontend
E2E_BASE_URL=http://localhost:3000 E2E_ADMIN_EMAIL=<admin> E2E_ADMIN_PASSWORD=<password> npm run e2e
```
