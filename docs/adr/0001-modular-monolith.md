# ADR-0001: Modular monolith with verified module boundaries

* Status: accepted
* Date: 2026-10-25

## Context

Almanook has nine distinct responsibilities (identity, cards, collection, discovery, expiration,
sourcing, enrichment, imagery, search). They share one database and one user. Traffic is personal
scale, and there is one team of one.

## Decision

Build one Spring Boot application. Each responsibility is a top-level package under
`app.almanook`. Spring Modulith's `ApplicationModules.of(AlmanookApplication.class).verify()` runs as
a unit test, so a module that reaches into another module's internal packages, or a dependency
cycle, fails the build. Cross-module calls go through each module's public application service.

## Consequences

* One deployable, one transaction manager, simple local development (`docker compose up` + `./mvnw`).
* Boundaries are explicit enough that, for example, `enrichment` could later be extracted behind an
  HTTP interface if AI workloads need separate scaling.
* No message broker. Module interactions are synchronous method calls.
