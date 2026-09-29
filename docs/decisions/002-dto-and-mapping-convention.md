# Decision 002 — API DTOs and Mapping Convention

## Problem

HTTP boundaries need explicit contracts: what the API accepts (`request`) and
what it returns (`response`). Options range from exposing JPA entities
directly to classic DTO classes with Lombok plus separate mapper components
or generated mappers (MapStruct). Without a convention, each new endpoint
chooses its own style, leaking persistence details into JSON or duplicating
mapping code inconsistently.

## Options considered

- **Expose entities directly** (`User` as request/response body): no extra
  classes, but leaks internal fields (e.g. future `passwordHash`), couples
  the JSON contract to persistence mappings, and risks lazy-loading and
  recursion issues during serialization.
- **Classic DTO classes** (Lombok `@Data`, `dto/` subpackage, separate
  injectable `UserMapper` or MapStruct): familiar enterprise style, scales
  well for nested/complex mappings, but adds boilerplate and a mapper
  abstraction for trivial field copies.
- **Records colocated in the domain package** with a static factory method:
  immutable, zero boilerplate, no extra dependencies, mapping lives with the
  DTO itself.

## Decision

Use Java records colocated in the domain package as DTOs, with a static
factory (`from(...)`) as the mapping point:

- `user/UserCreateRequest.java` — input contract, carries Bean Validation
  annotations (`@NotBlank`, `@Email`).
- `user/UserResponse.java` — output contract, `UserResponse.from(User)`.

No dedicated mapper class and no MapStruct dependency for now.

## Why

- Request and response are different contracts in both directions: the
  request does not include system-generated fields (`id`, `createdAt`) and
  carries validation; the response exposes exactly what the API offers.
- For small flat payloads (2–5 fields) a record plus factory is the smallest
  solution that keeps entities off the HTTP boundary (per project rule:
  prefer simple solutions over unnecessary abstractions).
- Records are inherently immutable; no Lombok needed on DTOs.
- The factory method is a single, testable translation point; renaming or
  reshaping the API contract touches only the record.

## Consequences

- Entities never cross the HTTP boundary; services accept/return DTOs and
  translate at the edge of the domain.
- When mappings become complex (nested entities, collections, field
  transformations), introduce MapStruct as a dedicated decision; do not
  accumulate growing manual mapping logic inside records.
- Validation annotations live on the request record; `@Valid` on the
  controller parameter triggers them, failures render as 400 via
  `ApiExceptionHandler`.
- New domains follow the same layout (`resource/`, `booking/`) so endpoint
  code stays predictable.
