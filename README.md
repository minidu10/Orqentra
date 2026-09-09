# Orqentra

An event-driven B2B supply chain ordering platform. Restaurants browse supplier
catalogs, place orders, and receive live confirmation as the order moves through
the system.

The business domain is deliberately simple. The engineering is the point.

## Architecture

Services coordinate an order through Kafka events and use the saga pattern to
stay consistent without distributed transactions. Each service owns its own
database and no service reads another's tables.

When an order is placed, the Order service writes it as `PENDING` and publishes
an event rather than calling Inventory directly. Inventory consumes the event,
reserves stock, and publishes the result. Order consumes that and moves to
`CONFIRMED` or `CANCELLED`. If a multi-item order partially fails, a
compensating event releases the stock already reserved.

## Stack

Java 21, Spring Boot, PostgreSQL, Flyway, Kafka, Docker, React.

## Running locally

```bash
docker compose up -d

cd inventory-service && ./mvnw spring-boot:run   # port 8081
cd order-service && ./mvnw spring-boot:run       # port 8080
```

The API is served on `http://localhost:8080`.

## Roadmap

- [x] Catalog with Flyway-managed schema
- [x] Order placement inside a single transaction
- [x] Split Order and Inventory over HTTP
- [x] Replace the HTTP call with Kafka events
- [x] Saga with compensating transactions
- [x] Idempotent consumers and transactional outbox
- [x] Retries and dead letter queue
- [ ] Auth, API gateway, React UI
- [ ] Tracing and metrics