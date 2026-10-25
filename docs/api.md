# REST API (v1)

All endpoints are under `/api/v1`, consume and produce JSON (except uploads), and require an
authenticated session unless marked *public*. The live OpenAPI 3.1 document is served at
`/v3/api-docs`, with Swagger UI at `/swagger-ui.html` (enabled by default in development, toggled with
`ALMANOOK_API_DOCS_ENABLED`).

## Conventions

* **Auth:** session cookie. State-changing requests must send the `X-XSRF-TOKEN` header with the value
  of the `XSRF-TOKEN` cookie. Call `GET /api/v1/auth/csrf` once to obtain the cookie.
* **Identity:** the user is always taken from the session. No endpoint accepts a user id.
* **Pagination:** `page` (0-based, default 0) and `size` (default 24, max 100). Responses use
  `{ "items": [...], "page": 0, "size": 24, "totalItems": 87, "totalPages": 4, "hasNext": true }`.
* **Dates:** ISO-8601 calendar dates (`2026-12-24`). Timestamps are ISO-8601 instants in UTC.
* **Money:** decimal numbers serialised as JSON numbers with up to two fraction digits, plus an
  ISO 4217 `currency`.
* **Errors:** [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `application/problem+json`:

```json
{
  "type": "https://almanook.app/problems/validation",
  "title": "Request validation failed",
  "status": 400,
  "code": "validation_failed",
  "detail": "2 fields are invalid",
  "errors": [{ "field": "title", "message": "must not be blank" }]
}
```

| Status | `code` examples |
|---|---|
| 400 | `validation_failed`, `malformed_request`, `unsafe_url` |
| 401 | `unauthenticated`, `bad_credentials` |
| 403 | `csrf_invalid` |
| 404 | `not_found` (also returned for another user's resources, so existence is not leaked) |
| 409 | `duplicate_card` (body has `existingCardId`), `email_taken`, `stale_version`, `already_in_collection` |
| 413 | `payload_too_large` |
| 415 | `unsupported_media_type` |
| 502/503 | `ai_provider_error`, `ai_unavailable`, `source_fetch_failed` |

Internal exception messages are never returned.

## Authentication and preferences

| Method | Path | Description |
|---|---|---|
| GET | `/auth/csrf` *(public)* | Issues the `XSRF-TOKEN` cookie. Returns 204. |
| POST | `/auth/register` *(public)* | `{email, password (≥ 10 chars), displayName}`. Returns 201 with `Me` and starts a session. Returns 409 `email_taken`. |
| POST | `/auth/login` *(public)* | `{email, password}`. Returns 200 with `Me`, or 401 `bad_credentials`. The session id is rotated on login. |
| POST | `/auth/logout` | Returns 204 and invalidates the session. |
| GET | `/me` | Returns `Me`: `{id, email, displayName, preferences: {theme, language, timezone}}`. |
| PUT | `/me/preferences` | `{theme: DARK\|LIGHT\|SYSTEM, language, timezone (IANA)}`. Returns the updated preferences. |

## Cards

`Card` (response):

```json
{
  "id": "0b8c…", "version": 3,
  "title": "Ogród Botaniczny PAN w Powsinie",
  "description": "A 40-hectare botanical garden on Warsaw's southern edge…",
  "itemType": "PLACE",
  "venueName": "Ogród Botaniczny – CZRB PAN", "address": "ul. Prawdziwka 2", "city": "Warszawa", "country": "Poland",
  "latitude": 52.1066, "longitude": 21.0947,
  "creator": null, "releaseYear": null,
  "sourceUrl": "https://www.ogrod-powsin.pl/",
  "price": { "min": 15.00, "max": 15.00, "currency": "PLN", "note": "Reduced 10 PLN", "source": "SOURCE" },
  "eventStartDate": null, "eventEndDate": null, "eventDatesSource": null,
  "expiresOn": null, "expirationSource": null, "expired": false,
  "openingHoursNote": null,
  "seasons": ["SPRING", "AUTUMN"],
  "cover": { "url": "https://upload.wikimedia.org/…", "source": "WIKIMEDIA", "attribution": "Photo: … / CC BY-SA 4.0", "attributionUrl": "https://commons.wikimedia.org/wiki/File:…", "license": "CC BY-SA 4.0" },
  "enrichment": { "mode": "AI", "sources": ["https://www.ogrod-powsin.pl/"], "unverifiedFields": ["openingHoursNote"], "confidence": {"overall": "MEDIUM"} },
  "originalInput": "Ogród Botaniczny w Powsinie",
  "collection": { "status": "ACTIVE", "savedAt": "…", "visitedAt": null, "personalNote": null },
  "discovery": null,
  "createdAt": "…", "updatedAt": "…"
}
```

| Method | Path | Description |
|---|---|---|
| POST | `/cards` | Creates a card. Request: `CardInput` plus `destination: COLLECTION\|DISCOVER\|NONE` (default `COLLECTION`), and optional `enrichment` metadata and `originalInput` taken from a draft. Returns 201 `Card`, or 409 `duplicate_card`. |
| GET | `/cards` | Lists or searches all of the user's cards (`q`, `season`, `sort`, `page`, `size`). |
| GET | `/cards/{id}` | Returns the card with the user's collection and discovery status. |
| PUT | `/cards/{id}` | Full update of the metadata. `CardInput` must include `version`; a stale version returns 409 `stale_version`. Fields the user changes get `*Source = USER`. |
| PATCH | `/cards/{id}/expiration` | `{expiresOn: "2026-12-24" \| null}`. Sets or clears the expiry (source `USER`). Clearing it, or moving it into the future, takes the card out of the archive. |
| DELETE | `/cards/{id}` | Permanently deletes the card and its entries. Returns 204. |

`CardInput` fields: `title` (required, ≤ 200), `description` (≤ 1200), `itemType` (required),
`venueName`, `address`, `city`, `country`, `latitude`/`longitude` (both or neither, in range),
`creator`, `releaseYear`, `sourceUrl` (http/https), `priceMin`, `priceMax` (≥ min), `currency`
(required with a price), `priceNote`, `eventStartDate`, `eventEndDate` (≥ start), `expiresOn`,
`openingHoursNote`, `seasons[]`, `cover {url | uploadId, source, attribution, attributionUrl, license}`,
and provenance hints `priceSource`, `eventDatesSource`, `expirationSource` (only `SOURCE` or `AI` are
accepted from a draft; anything the user edits becomes `USER`).

## Collection

| Method | Path | Description |
|---|---|---|
| GET | `/collection` | The active (non-expired, non-removed) collection. Query: `q` (title or description), `season` = `ALL` (default) \| `SPRING` \| `SUMMER` \| `AUTUMN` \| `WINTER` \| `NON_SEASONAL`, `sort` = `RECENT` (default) \| `ALPHABETICAL` \| `EVENT_DATE`, `page`, `size`. |
| PUT | `/collection/{cardId}` | Saves the card to the collection. Idempotent: returns 200 if it was already active, 201 otherwise. A pending inbox entry becomes `SAVED`. |
| DELETE | `/collection/{cardId}` | Soft-removes the card (status `REMOVED`). The card is kept, so "undo" is `PUT`. Returns 204. |
| PATCH | `/collection/{cardId}` | `{visited?: boolean, personalNote?: string}`. Visited is independent of expiry. |
| GET | `/expired` | The Expired archive: cards in the active collection or pending in Discover whose `expiresOn` is before today in the user's timezone. Same `q`, `page` and `size`; sorted by `expiresOn` descending. Each item has an `origin` of `COLLECTION` or `DISCOVER`. |

Season semantics: a single season returns cards tagged with that season; non-seasonal cards are
**not** included. `ALL` returns every card, including non-seasonal ones. `NON_SEASONAL` returns
cards with no season tags. `EVENT_DATE` sorts by `coalesce(eventStartDate, eventEndDate)`
ascending, with undated cards last.

## Discover inbox

| Method | Path | Description |
|---|---|---|
| GET | `/discover` | `status` = `PENDING` (default) \| `DISMISSED`, plus `q`, `season`, `page`, `size`. Expired pending cards are listed under `/expired`, not here. |
| POST | `/discover/{cardId}/save` | Moves the card to the collection (no duplicate is created). Returns the `Card`. |
| POST | `/discover/{cardId}/dismiss` | Sets status `DISMISSED`. Returns 204. |
| POST | `/discover/{cardId}/restore` | Moves a dismissed entry back to `PENDING`. Returns 204. |

New inbox items are created with `POST /cards` and `destination: DISCOVER`.

## Enrichment (AI)

| Method | Path | Description |
|---|---|---|
| GET | `/enrichment/status` | `{aiAvailable, provider, model, imageProviders: ["PAGE","WIKIMEDIA","UNSPLASH"], message}` |
| POST | `/enrichment/drafts` | Generates a **draft** (nothing is saved). Request: `{query?, url?, notes?, seasons?, expiresOn?}`; at least one of `query` or `url` is required. Returns `CardDraft`. |
| POST | `/enrichment/cover-candidates` | `{title, city?, sourceUrl?}` returns up to 8 `CoverCandidate`s for the "Choose another cover" picker. |

`CardDraft`:

```json
{
  "mode": "AI",                       // AI | BASIC (no AI key: page metadata only)
  "card": { …CardInput fields… },
  "fieldSources": { "eventEndDate": "SOURCE", "description": "AI", "seasons": "AI", "title": "USER" },
  "confidence": { "overall": "HIGH", "identification": "HIGH", "dates": "MEDIUM", "price": "LOW" },
  "unverifiedFields": ["openingHoursNote"],
  "missingFields": ["price"],
  "sources": [{ "url": "https://…", "title": "…" }],
  "coverCandidates": [{ "url": "…", "thumbnailUrl": "…", "source": "PAGE", "attribution": null, "license": null }],
  "possibleDuplicates": [{ "id": "…", "title": "…" }],
  "warnings": ["The source page could not be retrieved; facts come from web search only."]
}
```

When no AI provider is configured, `POST /enrichment/drafts` with a URL returns `mode: BASIC`, a draft
built only from the page's own metadata, with an explanatory warning. Without a URL it returns 503
`ai_unavailable`. A provider failure returns 502 `ai_provider_error`; a fabricated draft is never
returned in its place.

## Images

| Method | Path | Description |
|---|---|---|
| POST | `/images` | `multipart/form-data` upload, `file` field, JPEG, PNG or WebP up to 5 MiB. The format is checked by magic bytes. Returns 201 `{id, url}`. |
| GET | `/images/{id}` | Returns the image bytes. Only the owner can access it. Served with `Cache-Control: private, max-age=86400`. |

## Operations

| Path | Description |
|---|---|
| `/actuator/health` *(public)* | Liveness and readiness, including the database. |
| `/actuator/info` *(public)* | Build info. |
