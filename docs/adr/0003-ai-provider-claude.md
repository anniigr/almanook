# ADR-0003: Claude as the AI provider, behind a port, with a two-stage research/extraction call

* Status: accepted
* Date: 2026-10-25

## Context

Card enrichment must (a) find current facts about a place or event (dates, prices, venue),
(b) return them as structured, validated data, and (c) never present model memory as a verified
current fact. The provider must be replaceable.

## Options evaluated (October 2026)

| Option | Grounding in current web data | Structured output | Notes |
|---|---|---|---|
| Anthropic Claude Messages API | Server-side `web_search` tool (`web_search_20260209`) returns real result URLs and page content | JSON-schema `output_config.format` (constrained decoding) | Official Java SDK (`com.anthropic:anthropic-java`) |
| OpenAI Responses API | Built-in web search tool | JSON schema | Official Java SDK |
| Google Gemini | Search grounding | JSON schema | Java SDK via Vertex/GenAI |
| Self-hosted LLM | No grounding without our own search stack | Varies | Running cost and quality do not fit an MVP |

All three hosted options are workable. We chose Claude because it combines server-side web search
with result URLs that we can verify citations against, schema-constrained JSON, and a first-party
Java SDK. The `AiResearchProvider` interface keeps the alternatives open: an adapter returns
provider-neutral `AiCardProposal` objects plus the URLs it actually retrieved.

## Decision

* `enrichment.AiResearchProvider` is the port. `enrichment.provider.anthropic.AnthropicResearchProvider`
  is the adapter, enabled when `ANTHROPIC_API_KEY` is set.
* **Two calls per draft:**
  1. **Research:** model with the `web_search` tool (max 4 searches), asked to find official or
     reputable pages and report facts with their source URLs as plain notes. The adapter collects
     every `web_search_result` URL from the response, which is the evidence set.
  2. **Extraction:** the same model, **no tools**, with `output_config.format` set to the
     `AiCardProposal` JSON schema. Input: the user's input, the research notes and the fetched page
     snapshot (as untrusted data). Each date, price, address and opening-hours fact must carry a
     `sourceUrl`.
* `DraftAssembler` (provider-independent Java) then drops any fact whose `sourceUrl` is not in the
  evidence set (the user's fetched page plus the search result URLs), lists it as unverified, and
  never overwrites user-supplied fields.
* Splitting the stages avoids relying on structured output and server tools in the same request,
  which the structured-outputs documentation does not describe. It also keeps the extraction prompt
  free of tool noise.
* Default model `claude-opus-5-5` with low effort, configurable through `ALMANOOK_AI_MODEL` (for
  example `claude-sonnet-5-5` or `claude-haiku-5-5` to reduce cost). Server-side refusal fallback is
  requested. A `refusal` stop reason or invalid JSON is reported as an `ai_provider_error`, never
  patched over.

## Consequences

* Each draft costs two model calls plus search usage. For a personal collection this is cents per
  draft, and the model is configurable.
* Without a key the app is fully usable: manual cards, plus a BASIC import from a URL's own metadata
  (schema.org JSON-LD, Open Graph), labelled as such.
