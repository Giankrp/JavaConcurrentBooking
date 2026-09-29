# Decision 001 — Booking Interval Boundary Semantics

## Problem

Bookings occupy a time interval `[start, end]`. Overlap detection (core rule #5)
depends on whether interval endpoints belong to the booking. Under closed
endpoints, two consecutive bookings such as 10:00–11:00 and 11:00–12:00 would
conflict at the shared instant 11:00, preventing legitimate back-to-back
reservations. The SPEC requires this semantics to be defined before
implementing the final conflict logic.

## Options considered

- **Closed interval `[a, b]`**: both endpoints included. Conflict when
  `a.start <= b.end AND b.start <= a.end`. Rejects adjacent bookings.
- **Open interval `(a, b)`**: both endpoints excluded. Wastes both boundary
  instants and does not match how reservations are advertised to users
  ("10:00 to 11:00" is usable at 10:00).
- **Half-open interval `[a, b)`**: start included, end excluded. Conflict when
  `a.start < b.end AND b.start < a.end`. Adjacent bookings are allowed.

## Decision

Use half-open intervals `[start, end)` for booking conflict detection.
Bookings 10:00–11:00 and 11:00–12:00 on the same resource do not overlap.

## Why

- The shared boundary instant belongs to the later booking, so back-to-back
  usage is possible, which matches real meeting-room behavior.
- Durations compose exactly: `[10,11)` + `[11,12)` = 2 hours with no double
  counting of boundary instants.
- It is the de-facto standard for calendar and reservation systems
  (RFC 5545 `DTSTART`/`DTEND` semantics).

## Consequences

- Conflict predicate for active bookings on the same resource is:
  `new.start_time < existing.end_time AND existing.start_time < new.end_time`.
- A buffer or turnover time between bookings (e.g. 10 minutes) must NOT be
  encoded by shifting stored `end_time`; it would become a separate business
  rule applied in the conflict predicate, documented as its own decision if
  required.
- `start_time < end_time` (already enforced by the V1 `CHECK` constraint)
  remains valid; a zero-length booking is still invalid.
- Future PostgreSQL-based enforcement (exclusion constraints with
  `tsrange(...)`) must use the default `[)` range bounds, so no custom
  operator class is needed for this decision.
