# ADR-0002: Session cookies with SPA CSRF protection instead of JWT

* Status: accepted
* Date: 2026-10-25

## Context

The SPA and the API are served from the same origin (Vite proxies `/api` in development, nginx in
Docker). We need real authentication and authorization even though the first deployment target is
local or self-hosted.

## Decision

Use Spring Security form-less JSON login that creates a server-side HTTP session (`JSESSIONID`,
`HttpOnly`, `SameSite=Lax`, `Secure` in production). Protect state-changing requests with Spring
Security's SPA CSRF configuration (`csrf.spa()`: the `XSRF-TOKEN` cookie is echoed as the
`X-XSRF-TOKEN` header). Hash passwords with the delegating BCrypt encoder. A demo account can be
seeded by configuration for local single-user testing.

## Alternatives considered

* **JWT bearer tokens in localStorage:** exposed to XSS, revocation is hard, and they bring no
  benefit for a same-origin SPA.
* **OAuth2/OIDC with an external IdP:** a good fit for a public multi-tenant deployment, but it
  adds a hard dependency for local use. The `identity` module isolates this, so adding
  `spring-boot-starter-oauth2-client` later only changes the security configuration and the user
  lookup.

## Consequences

* Sessions are in memory, so a restart signs users out. Spring Session JDBC is a drop-in upgrade.
* Cross-origin deployment of the SPA would need CORS plus `SameSite=None; Secure` cookies.
