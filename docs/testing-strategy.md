# Testing strategy

The goal is that the behaviours users rely on (seasons, expiry, no duplicates, nothing silently
overwritten, no data leaking between users, no fake AI success) are pinned by automated tests that
run on every push.

## Test pyramid

| Layer | Tooling | What it covers | Command |
|---|---|---|---|
| Backend unit | JUnit 5, AssertJ, Mockito | `ExpirationPolicy` (timezone and DST boundaries), URL canonicalisation, `UrlSafetyPolicy` (hostile inputs), `PageExtractor` (JSON-LD and Open Graph fixtures), `DraftAssembler` (user fields win, uncited facts dropped, permanent types never get an expiry), AI output parsing and validation, season filter semantics | `./mvnw test` |
| Backend integration | Spring Boot Test, **Testcontainers PostgreSQL**, MockMvc | Flyway migrations and constraints, repository queries (search, season filter, pagination, sorting), REST API end to end with real security (CSRF, sessions, cross-user 404s), duplicate prevention, collection and discover state transitions, expired archive with a fixed `Clock` | `./mvnw verify` |
| Provider contract | WireMock + the real Anthropic Java SDK pointed at WireMock; WireMock as the Gemini Interactions API | Each adapter sends the expected request shape and parses research and extraction responses, and handles 4xx, 5xx, refusal and malformed JSON without fabricating a draft. The same approach covers the Wikimedia and Unsplash adapters. | `./mvnw verify` |
| SSRF | In-process HTTP server (`com.sun.net.httpserver`) | Redirect chains to private addresses, oversized bodies, slow responses and wrong content types. The test policy allows loopback only for that server and asserts that the production policy rejects it. | `./mvnw test` |
| Architecture | Spring Modulith `ApplicationModules.verify()` | Module boundaries and the absence of cycles | `./mvnw test` |
| Frontend unit and component | Vitest, Testing Library, MSW, jsdom | Navigation, season filter, search debounce, Add Card (generate, edit, save; manual creation; AI unavailable; AI error), Discover save and dismiss with undo, Expired restore, theme persistence and system preference, loading, empty and error states | `npm test` |
| End to end | Playwright (Chromium) against the Docker Compose stack | Sign in, then create a manual card, filter by season, search, save from Discover, expire a card by editing its date, restore it, and toggle the theme across a reload | `npm run e2e` |

## Principles

* **Deterministic time.** Every expiry test injects a `Clock`. Expiry tests in the database use a
  fixed `today` and never the wall clock.
* **No network in tests.** External HTTP goes to WireMock or an in-process server. The real
  Anthropic and Gemini APIs are never called in CI.
* **Real PostgreSQL.** Search uses `pg_trgm` and partial unique indexes, which H2 does not emulate
  correctly, so integration tests use Testcontainers (`postgres:17-alpine`).
* **Tests are written alongside the feature** they cover, in the same commit.

## Timezone and DST cases covered

* An event ending 2026-12-24 is active at 23:59:59 Europe/Warsaw on the 24th and expired at
  00:00:00 on the 25th.
* The same instant evaluated for a user in `America/Los_Angeles` and one in `Europe/Warsaw` gives
  different results around midnight UTC.
* DST boundaries in Europe/Warsaw (2026-03-29 and 2026-10-25): the local date is computed correctly
  across the shift, so 23 and 25-hour days do not move an expiry by a day.
* `Pacific/Kiritimati` (UTC+14) and `Pacific/Pago_Pago` (UTC-11) as extreme offsets.
* A missing expiry never expires. Clearing the expiry, or moving it into the future, restores the
  card.

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs three jobs on every push and pull request:

1. **backend:** `./mvnw -B verify`, which includes the Spotless formatting check, unit and
   integration tests (Testcontainers uses the runner's Docker), and a JaCoCo report.
2. **frontend:** `npm ci`, `npm run lint`, `npm run typecheck`, `npm test`, `npm run build`.
3. **e2e:** builds the Docker images, starts Compose, waits for health, runs Playwright, and
   uploads the report on failure.
