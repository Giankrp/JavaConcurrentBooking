# SPEC.md

## Goal

Build a REST backend for reserving shared resources.

The project is primarily focused on backend engineering and concurrency
correctness rather than frontend development.

## Domain

### User

A person who can make reservations.

### Resource

Something that can be reserved.

Initial example: meeting rooms.

### Booking

A reservation connecting a user and a resource to a time interval.

Conceptually:

    User 1 ─── N Booking N ─── 1 Resource

## Core Rules

1. A resource must exist and be active to be reserved.
2. `startTime` must be before `endTime`.
3. A booking belongs to exactly one user and one resource.
4. Cancelled bookings do not block availability.
5. Active bookings for the same resource must not overlap.
6. The system must preserve rule #5 under concurrent requests.

Boundary semantics of time intervals are already defined: bookings use
half-open intervals `[start, end)`, so adjacent bookings do not conflict.
See `docs/decisions/001-booking-interval-semantics.md`.

## Initial API

Users:

    POST /api/users
    GET /api/users/{id}

Resources:

    POST /api/resources
    GET /api/resources
    GET /api/resources/{id}

Bookings:

    POST /api/bookings
    GET /api/bookings/{id}
    GET /api/bookings?resourceId={id}
    DELETE /api/bookings/{id}

`DELETE /api/bookings/{id}` cancels the booking (sets `status` to
`CANCELLED`); it does not delete the row. Cancelled bookings keep existing
but do not block availability (rule #4).

The API may change as the domain evolves.

## Persistence

PostgreSQL is the primary database.

Flyway manages schema migrations.

Initial tables:

    users
    resources
    bookings

Use database constraints and indexes where they represent real domain
requirements or actual query patterns.

## Concurrency

Concurrency correctness is a core requirement.

The naive approach:

    check availability
    create booking

must not be assumed to be safe under concurrent requests.

We will explicitly test scenarios such as:

    1 resource
    same time interval
    N concurrent clients

Expected invariant:

    At most one conflicting active booking succeeds.

The concurrency strategy has not yet been finalized. Investigate and compare
appropriate PostgreSQL/Spring approaches before choosing one.

Scope update: overlap enforcement (rules #5 and #6) was pulled forward into
Phase 2 at the user's request. A PostgreSQL exclusion constraint on active
bookings (documented in an upcoming decision) provides the database-level
guarantee. Dedicated concurrency tests remain Phase 5; the Go load generator
remains Phase 7.

## Testing

Important tests include:

- invalid time intervals
- nonexistent resources
- inactive resources
- overlapping bookings
- adjacent bookings
- cancellation
- concurrent booking attempts

Database-sensitive tests should use PostgreSQL through Testcontainers.

## Load Testing

A separate Go program will eventually generate concurrent HTTP traffic against
the Java service.

Its purpose is to:

- simulate many clients;
- reproduce contention;
- measure latency and throughput;
- verify reservation correctness under load.

It is a testing tool, not part of the Java application's runtime.

## Roadmap

### Phase 1
Project setup, PostgreSQL, Flyway and basic domain.

### Phase 2
Resources and bookings REST API.

### Phase 3
Business rules and validation.

### Phase 4
Unit and integration tests.

### Phase 5
Concurrency correctness.

### Phase 6
Authentication and authorization.

### Phase 7
Go load generator and benchmarks.

### Phase 8
Docker/CI/observability improvements as needed.

Only work on the current phase unless explicitly asked otherwise.

## Current Phase

**Phase 2 — Resources and bookings REST API (in progress).**

- Phase 1 (project setup, PostgreSQL, Flyway, basic domain) is complete.
- Done in Phase 2: users API, temporary security config opening `/api/**`
  and `/actuator/health` until authentication lands in Phase 6.
- Next: resources and bookings endpoints, including the pulled-forward
  overlap enforcement (PostgreSQL exclusion constraint).
- Massive load testing remains Phase 5/7.
