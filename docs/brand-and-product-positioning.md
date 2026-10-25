# Brand and product positioning

## Positioning statement

**Almanook is a private, image-led almanac of the places and experiences you want to get to.**
Paste a name, a link or a short note; Almanook turns it into a calm, informative card — cover,
venue, dates, price — with every fact traceable to a source. It knows which ideas suit which season,
quietly moves finished exhibitions to an archive, and keeps unsaved ideas in a separate Discover
inbox so the collection stays intentional.

Primary market: culture-goers in Poland (Warsaw, Kraków and the large cities). It works worldwide.

## Why this position (from the research)

The [market research](market-research.md) found three kinds of tools, and each one leaves part of
the problem unsolved:

| Tool family | Strength | What it misses for "things I want to do" |
|---|---|---|
| Place savers (Google Maps lists, Mapstr, Stasht) | Location, quick capture | No dates, so exhibitions and concerts become stale pins; lists are cluttered and not image-led |
| Event tools (Soonlist, Eventbrite, Fever, Going.) | Dates, tickets | Events only, often only their own inventory; no place for a botanical garden or a film |
| Visual bookmarking (Pinterest, mymind, Raindrop) | Beautiful grids, AI tagging | No venue/date/price structure, no expiry, increasing noise (ads, AI-generated imagery) |

Almanook combines the **visual grid** of bookmarking tools with a **structured, time-aware card**
model. In the products we reviewed, we did not find the following together: multi-season tagging,
query-time expiry with a non-destructive archive, a separate unsaved inbox, and enrichment that shows
its sources on the card. We make no claim that no product anywhere does any of these individually.

## Differentiators we commit to

1. **Name, URL or note → informative card.** Enrichment is grounded in real pages: a URL the user
   gives, or pages found by web search, preferring official venue and organiser sites.
2. **A real cover.** We try the official page's image first, then openly licensed images from
   Wikimedia Commons (with attribution), then Unsplash (if configured), and otherwise use a neutral
   placeholder.
3. **Only what matters.** The description is two or three sentences. Exhibitions put venue, end date
   and price first; permanent places put location first; films show identification and watch status.
4. **Seasons and expiry are first-class, and independent of each other.** A card can be in several
   seasons. Temporary events expire at the end of their last local day, and nothing is ever deleted
   because of it.
5. **Collection vs Discover.** Saving is a deliberate act; ideas wait in the inbox until you save or
   dismiss them.
6. **Honest AI.** Unknown facts stay unknown, uncited dates and prices are dropped, and the card
   shows which fields are unverified.

## Naming

### Directions explored

| Direction | Ideas explored |
|---|---|
| Time and seasons | Somewhen, Soonish, Tempora, Seasonly, Seasonaut, Tidely |
| Elsewhere / wandering | Hither, Hitherly, Yonderly, Wanderwhen, Wanderkeep |
| Collections and keepsakes | Ephemera, Curio, Keepsake, Driftlist, Wistly |
| Card and place | Placard, Placemint, Kartka (Polish for "card" or "sheet") |
| Almanac + personal corner | **Almanook**, Almanac variants |

### Screening (checked 2026-10-25)

| Candidate | Product conflict found | Domains | GitHub | Verdict |
|---|---|---|---|---|
| Soonish | Two iOS apps named *Soonish*, one of them for saving places ("Let's Go Here"); *Soonlist* is a direct event competitor | — | — | Rejected: direct conflict |
| Ephemera | iOS app *Ephemera: Expiring Bookmarks* uses the same expiry concept | — | — | Rejected: direct conflict |
| Hither | No product found, but it is a common English word | hither.app, .com, .io taken | Repos named `hither` exist | Rejected: hard to search, no domain |
| Somewhen | No product found | .app, .com, .io taken | — | Rejected: no clean domain |
| Wanderwhen | No product found; the `wander*` space is crowded (Wanderlog, Wanderlist, Wander) | .app available, .com taken | `wanderwhen/wanderwhen` exists | Rejected: too close to Wanderlog |
| Seasonaut | No product found | .app available | free | Runner-up: covers seasons but not places or collections |
| **Almanook** | **No web results for the exact name; no GitHub repositories** | **almanook.app available**; .com registered | `anniigr/almanook` free | **Chosen** |

Domain availability was checked with a registrar availability lookup (read-only, nothing reserved).
"No web results" is a web-search result, not a trademark clearance. Before any commercial launch,
run a formal EUIPO / UPRP (Polish Patent Office) / USPTO search in classes 9, 41 and 42.

### Rationale for "Almanook"

* **Almanac + nook.** An almanac is the classic seasonal reference: dates, events and what is
  worth doing when. A nook is a small private corner. Together they describe a personal, seasonal
  reference of places and events.
* **Pronunciation:** *AL-ma-nook*. It has three syllables, is spelled the way it sounds in English,
  and is easy to say in Polish (*al-ma-nuk*).
* **Distinctive and searchable:** the exact string returned no results in our checks, so search
  results will point at the product.
* **No forced "AI".** The AI is a capability, not the identity. An "-ai" suffix felt bolted on, and
  the research shows users growing tired of AI-heavy products (Pinterest complaints). The brand
  promises calm and trust; the AI is presented as "Generate card".

### Usage

* Product name: **Almanook** (one word, capital A only).
* Repository: `almanook`; Java base package `app.almanook`; Docker service prefix `almanook-`.
* Tagline: *Your almanac of places and plans.*
* Voice: editorial, brief and concrete. No exclamation marks, no superlatives about places, no
  invented facts.

## Visual identity (summary)

* Dark-first, deep charcoal surfaces (`#141416` base, `#1c1c1f` elevated), warm off-white text, and
  a single accent: a muted amber ("lamplight", `#d9a35b`) that evokes a reading nook. A light theme
  uses warm paper tones (`#f7f4ee`).
* Typography: system UI sans for the interface, with a serif (Georgia / "Iowan Old Style") for card
  titles to give an editorial almanac feel.
* Images carry the colour. The interface stays neutral so covers stand out.
