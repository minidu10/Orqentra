# ChainSync

Event-driven B2B supply chain ordering platform. Restaurants order stock from
suppliers; independent services coordinate the order through Kafka events,
using the saga pattern to stay consistent without distributed transactions.

## Status

In development. Building as a modular monolith first, then extracting services.

- [x] Phase 0 — Project setup, catalog read API
- [ ] Phase 1 — Ordering and inventory in one transaction
- [ ] Phase 2 — Split over HTTP
- [ ] Phase 3 — Kafka between services
- [ ] Phase 4 — Saga with compensating transactions
- [ ] Phase 5 — Idempotency, outbox, dead letter queue

## Stack

Java 21, Spring Boot, PostgreSQL, Flyway, Kafka, Docker.

## Running locally

Requires JDK 21 and Docker Desktop.

```bash
docker compose up -d
cd platform
./mvnw spring-boot:run
```

API available at `http://localhost:8080`.