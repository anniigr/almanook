# Implementation plan

Work is delivered in vertical stages. Each stage ends with green tests and at least one commit.
All twelve stages were completed on 2026-10-25; Git history is the authoritative record of the order.

| # | Stage | Scope | Done when |
|---|---|---|---|
| 1 | Research and planning | Market research, naming, positioning, architecture, data model, API, security and testing docs, ADRs | Docs committed |
| 2 | Foundation | Maven project (Boot 4.1.2, Java 21), Flyway V1 schema, Docker Compose (PostgreSQL), `.env.example`, Spotless, Modulith verification test, Vite + React + TS strict + Tailwind scaffold, CI workflow | `./mvnw verify` and `npm run build` pass locally and in CI |
| 3 | Identity and security | Register, login, logout, `/me`, preferences, CSRF for the SPA, demo user for development, problem-detail errors | Auth integration tests pass, including cross-user 404s |
| 4 | Cards and collection | Card CRUD, seasons, provenance, canonical URL with duplicate detection, collection save, remove and visited, search, season filter, sorting, pagination, optimistic locking | Repository and API integration tests pass |
| 5 | Discover and expiration | Inbox add, dismiss, restore and save; `ExpirationPolicy`; expired archive; expiry edits | Timezone and DST unit tests and archive integration tests pass |
| 6 | Frontend shell | Sidebar, header (title, search, Add Card, theme toggle, user menu), design tokens, dark and light themes, auth screens | Component tests for navigation and theme |
| 7 | Frontend features | Collection grid (4/3/2/1 columns, infinite scroll), filters and sort, card overflow menu, card detail and edit dialog, Discover, Expired, Settings | Component tests for each screen |
| 8 | Sourcing | `UrlSafetyPolicy`, `SafeHttpFetcher` (Apache HttpClient 5 with a validating DNS resolver), `PageExtractor` (JSON-LD, OG) | SSRF test table passes |
| 9 | AI enrichment | `AiResearchProvider` port, Anthropic adapter (research + structured extraction), `DraftAssembler`, BASIC mode without a key, `/enrichment/*` endpoints, Add Card generate flow in the UI | WireMock contract tests pass and the UI flow is tested with MSW |
| 10 | Imagery | Page image, Wikimedia Commons, Unsplash (optional), uploads, cover picker UI, fallback placeholder | Adapter tests and upload tests |
| 11 | Quality | Accessibility pass (keyboard, focus, dialogs, contrast), error and empty states, security headers, Playwright E2E, performance (indexes, `@BatchSize`, image lazy-loading) | E2E passes against Compose |
| 12 | Publication | README with real screenshots, diagrams and setup; final clean-clone verification; push | Fresh clone builds and runs from the README alone |

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| No AI API key is available in the development environment | The adapter is tested through the real SDK against WireMock. BASIC mode keeps URL import useful without a key. The live check is documented as a manual step. |
| Combining structured output with server tools in one call is not documented | Use two calls: research with web search returns free text and source URLs, then extraction with the JSON schema and no tools ([ADR-0003](adr/0003-ai-provider-claude.md)). |
| Hotlinked images disappear | Covers degrade to a placeholder on the client (`onError`). The card stays usable. |
| Venue sites block bots | Partial evidence is used and the problem is surfaced as a draft warning. Web search continues. |
