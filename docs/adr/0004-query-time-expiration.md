# ADR-0004: Expiration is computed at query time in the user's timezone

* Status: accepted
* Date: 2026-10-25

## Context

Temporary events (exhibitions, festivals, Christmas markets) should leave the active collection after
their last day and appear in an archive. The app may be offline for days. Users can live in any
timezone (default Europe/Warsaw).

## Decision

Store only `expires_on DATE` and its provenance. A card is expired when
`expires_on < today`, where `today = LocalDate.now(clock.withZone(user.timezone))` is computed in
Java and bound as a query parameter. Collection queries add `(expires_on IS NULL OR expires_on >=
:today)` and the archive query adds `expires_on < :today`. No scheduled job is involved.

## Consequences

* Always correct after downtime. Changing the date immediately moves the card in either direction.
* `ExpirationPolicy` is a pure function, unit-tested across DST changes and extreme offsets.
* A future scheduled job (for example "ends this week" notifications) can be added without being
  responsible for correctness.
* Default expiry: for temporary item types, the verified `event_end_date`. Permanent types (places,
  museums, restaurants, films) never get one unless the user sets it.
