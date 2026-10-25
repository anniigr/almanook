# Security

## Threat model (summary)

| Asset | Threat | Mitigation |
|---|---|---|
| A user's private collection | Another user reads or modifies it (IDOR) | All queries are scoped by the session principal. A resource owned by someone else returns `404`. A composite FK forces entries to belong to the card owner. Tests cover cross-user access on every resource type. |
| Accounts | Credential stuffing, weak storage | BCrypt via `DelegatingPasswordEncoder`. Minimum password length 10. Generic `bad_credentials` message. The session id is rotated on login. |
| Session | CSRF, cookie theft | `HttpOnly`, `SameSite=Lax` cookie (`Secure` in the `prod` profile). Spring Security SPA CSRF: the token is in the `XSRF-TOKEN` cookie and must be echoed in the `X-XSRF-TOKEN` header. |
| Backend network position | SSRF through "paste a URL" | `UrlSafetyPolicy` + `SafeHttpFetcher`, see below. |
| AI pipeline | Prompt injection from fetched pages | Page content is passed as delimited, untrusted *data*. The model has no tools that act on the user's data. Outputs are schema-validated and facts must cite retrieved URLs. |
| AI / image credentials | Leakage | Only server-side environment variables. Never logged, never sent to the browser, never committed. `.env` is git-ignored; `.env.example` contains placeholders only. |
| Uploads | Malicious files, storage exhaustion | 5 MiB limit, allow-list of JPEG/PNG/WebP checked by magic bytes (not only by `Content-Type`). Served with the stored content type and `X-Content-Type-Options: nosniff`. Only the owner can read them. |
| Error responses | Information leakage | RFC 9457 problem responses with fixed messages. Stack traces and SQL errors are logged server-side only. |
| Browser | XSS | React escapes output. No `dangerouslySetInnerHTML`. AI and page text are rendered as plain text. Security headers: `Content-Security-Policy`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`. |

## SSRF protections (`app.almanook.sourcing`)

Every user-supplied URL (source pages, image URLs that the server validates) goes through
`SafeHttpFetcher`, which applies these rules on **every hop**:

1. **Scheme:** only `http` and `https`. `file:`, `ftp:`, `gopher:`, `data:`, `jar:` and similar
   schemes are rejected.
2. **Credentials in the URL** (`user:pass@host`) are rejected.
3. **Port:** only 80, 443, or no port. This prevents probing internal services on non-standard ports.
4. **Host resolution:** the host is resolved once, and **every** resolved address must be public.
   Rejected ranges: loopback (`127.0.0.0/8`, `::1`), unspecified (`0.0.0.0/8`, `::`), private
   RFC 1918 (`10/8`, `172.16/12`, `192.168/16`), link-local (`169.254/16`, including the cloud
   metadata address `169.254.169.254`, and `fe80::/10`), carrier-grade NAT (`100.64/10`), unique
   local IPv6 (`fc00::/7`), multicast, broadcast, benchmark (`198.18/15`), documentation and other
   reserved ranges, and IPv4-mapped IPv6 forms of all of these. Hostnames such as `localhost`,
   `*.localhost`, `metadata.google.internal` and `*.internal` are rejected before resolution.
5. **DNS rebinding:** the connection is made to the **validated IP address**, with the original
   `Host` header and TLS SNI kept. A second DNS answer cannot redirect the request.
6. **Redirects** are followed manually (maximum 5). Each `Location` is resolved against the current
   URL and re-validated from step 1. An `https` → `http` downgrade is allowed only for redirects
   that stay on the same host.
7. **Limits:** connect timeout 4 s, total request timeout 8 s, and a response body cap of 2 MiB for
   pages (5 MiB for images), enforced while streaming. A `Content-Length` header is not trusted.
8. **Content type:** pages must be `text/html` or `application/xhtml+xml`; images must be
   `image/jpeg`, `image/png`, `image/webp` or `image/gif`.
9. **Identification:** requests are sent with a descriptive `User-Agent` (`AlmanookBot/1.0 (+repo
   URL)`), as Wikimedia's API etiquette requires.

The policy is unit-tested with a table of hostile inputs (decimal, octal and hex IP encodings,
IPv6 literals, mapped addresses, redirects to metadata endpoints) and integration-tested with an
in-process HTTP server.

## Prompt-injection posture

* The model gets a system prompt that defines the task. Retrieved page text is placed in a user
  message inside `<untrusted_page>` tags, with an explicit instruction that it is data and that any
  instructions inside it must be ignored.
* The model's only tools are Anthropic's server-side `web_search` (research stage). The extraction
  stage has no tools at all. Even a successful injection can at most produce a wrong draft, and the
  user reviews every draft before saving.
* Structured output is validated against the schema and then by Bean Validation. Dates, prices and
  addresses must name a source URL that was actually retrieved in this request (the fetched page or
  a web-search result URL); otherwise they are discarded and listed as unverified.

## Configuration secrets

| Variable | Purpose |
|---|---|
| `ANTHROPIC_API_KEY` | Enables AI enrichment. If it is missing, the app runs with AI disabled. |
| `UNSPLASH_ACCESS_KEY` | Optional third image source |
| `POSTGRES_PASSWORD` / `SPRING_DATASOURCE_PASSWORD` | Database |
| `ALMANOOK_DEMO_USER_PASSWORD` | Only used when the demo user is enabled (development) |

## Known gaps (tracked in the roadmap)

* No rate limiting on login or enrichment yet. A reverse proxy limit or bucket4j is the next step.
* No email verification or password reset. The MVP targets self-hosted, personal use.
* Sessions are in memory, so a restart signs users out. Spring Session JDBC is a drop-in change.
