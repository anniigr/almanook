# ADR-0006: Google Gemini as a second AI provider

* Status: accepted
* Date: 2026-10-25

## Context

[ADR-0003](0003-ai-provider-claude.md) put AI enrichment behind the `AiResearchProvider` port with
Claude as the only adapter. The repository owner has a Gemini API key but no Anthropic key, so AI
enrichment could not be used at all. Gemini offers the two capabilities the design needs: grounding
in current web data and JSON-schema-constrained output.

## Decision

* Add `GeminiResearchProvider` beside the Claude adapter, using the same two stages, prompts and JSON
  schema (now shared in `enrichment.provider`):
  1. Research: `POST /v1beta/interactions` with the `google_search` and `url_context` tools.
     Evidence URLs are the `url_citation` annotations, URLs in Google Search results, and
     `url_context` results whose status is a success.
  2. Extraction: the same endpoint with `response_format` (`application/json` plus the card schema) and
     no tools.
* Use Google's Interactions API, which its documentation now uses for grounding and structured
  output, called through the JDK `HttpClient`. This avoids adding a second SDK and its transitive
  dependencies for two POST requests.
* Send `store: false` so Google does not retain interactions.
* Choose the provider with `ALMANOOK_AI_PROVIDER`: `auto` (Anthropic if its key is set, otherwise
  Gemini), `anthropic`, `gemini` or `none`. An explicit choice never falls back silently to the other
  provider. Default model: `gemini-3.8-flash`, which Google recommends for new projects.
* Failure semantics are the same as for Claude. HTTP errors, a `failed` or `incomplete` status, empty
  output (for example a safety block) and invalid JSON all become `AiProviderException`; nothing is
  invented. Rate-limit and 5xx responses are retried twice with a short backoff.

## Consequences

* `DraftAssembler` is unchanged: a fact still counts only if it cites a URL that was actually
  retrieved, whichever provider produced it.
* Google Search citations can be redirect links. They are resolved by reading one `Location` header
  from Google's redirect host; links that do not resolve are dropped as evidence (see
  [security.md](../security.md)).
* Google does not document every field of the tool-result steps, so the parser reads them
  defensively. The adapter is contract-tested against WireMock but has not yet been run against the
  live API.
