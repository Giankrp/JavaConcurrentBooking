# Decision 003 — Booking Overlap Enforcement

## Problem

The application-level overlap check (`existsConflict` before insert) only
protects sequential requests. Under READ COMMITTED, two concurrent
transactions can both observe "no conflict" before either commits and both
insert overlapping active bookings, breaking core rules #5 and #6. A
guarantee under race is required, not a best-effort filter.

## Options considered

- **Application check only**: already in place; proven unsafe under race
  (both transactions pass the check).
- **`SELECT ... FOR UPDATE`**: locks existing rows only; it does not lock
  the absence of rows, so two inserts into a free interval still race.
- **`SERIALIZABLE` isolation with retries**: correct but forces retry
  handling in every writer and raises serialization failures globally,
  affecting unrelated writes.
- **Application-side advisory locks**: works, but keeps the arbiter outside
  the database; fragile across multiple app instances and bypassable by any
  new writer.
- **PostgreSQL partial exclusion constraint**: `EXCLUDE USING gist` on
  `(resource_id WITH =, tstzrange(start_time, end_time, '[)') WITH &&)`
  limited to `WHERE (status = 'ACTIVE')`. The database serializes conflicting
  inserts at index time; the loser waits for the winner's commit and then
  receives a constraint violation.

## Decision

Enforce overlap at the database level with a partial exclusion constraint
(migration `V2__booking_no_overlap.sql`), using the `btree_gist` extension to
combine equality on `resource_id` with range overlap. The service-level
`existsConflict` check remains as a fast path for sequential requests, and the
constraint violation is translated to `BookingConflictException` (HTTP 409).

## Why

- The database is the only point where a racing insert can be serialized:
  the constraint is evaluated per row at insert time against committed
  state, regardless of which client or code path performs the write.
- It covers empty gaps, which row locks cannot.
- It is automatic for every future writer (new endpoints, scripts, manual
  SQL) with no retry logic to maintain.
- The partial predicate uses `[)` bounds, matching Decision 001, and
  cancelled bookings leave the constraint scope, freeing the interval
  (core rule #4).

## Consequences

- Requires the `btree_gist` extension (equality on `bigint` inside a GiST
  index); it is created in `V2` and is a small, standard extension.
- Every insert/update on `bookings` pays the GiST index cost; negligible at
  this scale and indexed on the columns the conflict check queries anyway.
- A rejected insert surfaces as a unique/exclusion violation: only violations
  of `excl_bookings_no_overlap` are translated to conflict (409); other
  integrity errors keep their normal handling.
- Dedicated concurrency tests prove the invariant (one winner per interval);
  the Go load generator (Phase 7) will reproduce contention at HTTP scale.
