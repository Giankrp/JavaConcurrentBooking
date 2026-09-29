# AGENTS.md

## Project

This is a backend resource reservation system built to practice and demonstrate
modern Java/Spring backend engineering.

The main technical focus is correctness under concurrent access, especially
preventing conflicting reservations when multiple clients attempt to reserve
the same resource simultaneously.

## Stack

- Java 25
- Spring Boot 4
- Maven
- Spring Web
- Spring Data JPA
- Spring Security
- PostgreSQL
- Flyway
- JUnit 5
- Testcontainers
- Docker / Docker Compose

A separate Go program will eventually be used as a concurrent load generator
for the Java API.

## Architecture

Start as a modular monolith.

Organize code primarily by domain:

    user/
    resource/
    booking/

Avoid premature microservices or unnecessary infrastructure.

Do not introduce a dependency or technology unless it solves a concrete
problem in the current project.

## Important Engineering Rules

- Inspect existing code before making changes.
- Prefer small, incremental changes.
- Do not rewrite working code without a reason.
- Keep business logic testable.
- Use PostgreSQL for database behavior that depends on PostgreSQL semantics.
- Use Flyway for schema changes.
- Do not use H2 as a substitute for PostgreSQL integration tests.
- Do not assume sequential tests prove concurrency correctness.
- For concurrency-sensitive behavior, verify both application behavior and
  final database state.
- Prefer simple solutions over unnecessary abstractions.
- Do not add Kafka, Redis, Kubernetes, etc. unless a concrete requirement
  appears.
- Use Lombok for JPA entities: class-level `@Getter`, field-level `@Setter`
  on mutable fields only, `@NoArgsConstructor(access = PROTECTED)` for JPA.
  Use `@Slf4j` for loggers. Never use `@Data` on entities: equals/hashCode
  across JPA relations is unsafe.
- DTOs are Java records colocated in the domain package with a static
  factory (see `docs/decisions/002`). JPA entities never cross the HTTP
  boundary.
- Use explicit constructor injection with `final` fields. Do not use
  field-level `@Autowired`.
- Logging: successful domain events at INFO with id and key fields;
  rejected business operations at WARN before mapping to the HTTP error.
  Adjust HTTP request verbosity via properties, not code.
- Local development runs PostgreSQL via Docker Compose on port 5433 to
  avoid clashing with other local PostgreSQL instances.
- Testcontainers is 2.x with an explicit BOM (the Spring Boot 4 parent no
  longer manages it) and the `testcontainers-*` artifact names.

## Testing

Use:

- unit tests for isolated business logic;
- Spring integration tests for application/database behavior;
- Testcontainers with PostgreSQL when real database behavior matters;
- dedicated concurrency tests for reservation correctness.

## Commands

Use Maven for building and testing.

Typical commands:

    ./mvnw test
    ./mvnw verify
    ./mvnw spring-boot:run

Use Docker Compose for local PostgreSQL infrastructure.

## Working With Tasks

Before making any change:

1. Read this file.
2. Read `docs/SPEC.md`.
3. If `docs/decisions/` exists, read the decisions relevant to the change.
4. Inspect the relevant existing code.
5. Identify the smallest reasonable change.
6. Implement it.
7. Run the relevant tests.
8. Report important decisions or unresolved issues.

Do not implement future roadmap items unless explicitly requested.
