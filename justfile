default:
    @just --list

# Start local PostgreSQL via Docker Compose (port 5433)
db-up:
    docker compose up -d db

# Stop local PostgreSQL and remove containers
db-down:
    docker compose down

# Show compose status
db-status:
    docker compose ps

# psql shell against the local database
psql:
    docker compose exec db psql -U booking -d bookingdb

# Follow PostgreSQL logs
db-logs:
    docker compose logs -f db

# Run the Spring Boot application against the local database
run:
    ./mvnw spring-boot:run

# Run the full test suite (needs Docker for Testcontainers)
test:
    ./mvnw test

# Run the Go load generator against a running API (just load-test URL N)
load-test url="http://localhost:8080" n="50":
    cd loadgen && go run . -url {{url}} -n {{n}}

# Build and run all checks including integration tests
verify:
    ./mvnw verify
