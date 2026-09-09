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

## Services

| Service | Port | Owns |
|---|---|---|
| `api-gateway` | 8080 | The only port a client should use |
| `inventory-service` | 8081 | Stock levels |
| `payment-service` | 8082 | Payment attempts |
| `auth-service` | 8083 | Users, restaurants, JWT issuing |
| `order-service` | 8084 | Orders, catalog, admin API |
| `notification-service` | 8085 | Live SSE stream, no database |
| `web` | 5173 | React UI |

## Running locally

```powershell
.\dev.ps1          # infrastructure, all six services, and the UI
.\dev.ps1 -Stop    # stop the services and the dev server
```

Or by hand:

```powershell
docker compose up -d
# then, each in its own terminal:
cd auth-service         ; .\mvnw.cmd spring-boot:run
cd inventory-service    ; .\mvnw.cmd spring-boot:run
cd payment-service      ; .\mvnw.cmd spring-boot:run
cd order-service        ; .\mvnw.cmd spring-boot:run
cd notification-service ; .\mvnw.cmd spring-boot:run
cd api-gateway          ; .\mvnw.cmd spring-boot:run
cd web                  ; npm install; npm run dev
```

- UI: `http://localhost:5173`
- API: `http://localhost:8080` (everything goes through the gateway)
- Redpanda console: `http://localhost:8090`

### Seeded accounts

Development only. Passwords are stored as BCrypt hashes; these are the plaintexts.

| Email | Password | Role | Restaurant |
|---|---|---|---|
| `alice@orqentra.test` | `alice-pass` | `RESTAURANT` | Alice Trattoria |
| `bob@orqentra.test` | `bob-pass` | `RESTAURANT` | Bob Bistro |
| `admin@orqentra.test` | `admin-pass` | **`ADMIN`** | sees the Admin screens |

`admin@orqentra.test` is the one with the admin role: it can read any restaurant's
orders, reach `/api/admin/**`, and subscribe to every restaurant's event stream.

### Watching the event stream

`.\watch-stream.ps1` logs in and tails the SSE stream in a terminal, which is useful
alongside the UI when you want to see the raw events.

## Observability

| Tool | URL | What it is for |
|---|---|---|
| Jaeger | `http://localhost:16686` | One order, one trace, across every service |
| Prometheus | `http://localhost:9090` | Raw metrics and target health |
| Grafana | `http://localhost:3001` | The dashboard below (anonymous viewer, or admin/admin) |
| OTel collector | `:4318` OTLP in | Receives traces and forwards them to Jaeger |

Everything comes up with `docker compose up -d`; the datasource and the dashboard are
provisioned from files in `observability/`, so nothing is lost to `docker compose down -v`.

### The dashboard

`Orqentra — service and saga health`. The top row answers "is anything wrong right now"
without scrolling: consumer lag, outbox depth, dead letter count, 5xx rate, services up.
Below that: request and error rate by service, p50/p95/p99 latency, consumer lag by group
and topic, outbox depth, saga outcomes and duration, and open SSE connections.

Consumer lag is the one to watch. Every service can be up and every request fast while
orders quietly fall minutes behind because one consumer stopped keeping pace.

### Finding one order's trace

1. Place an order and note the reference from the response.
2. Open Jaeger, choose service `orqentra-api-gateway`, operation `http post`, and Find Traces.
3. The saga is a single trace, roughly 26-30 spans across five services:

```
[api-gateway]  http post
  [order-service]  http post /api/orders
    [order-service]  outbox publish order.created
      [inventory-service]  order.created process
        [inventory-service]  outbox publish stock.reserved
          [payment-service]  stock.reserved process
            [payment-service]  outbox publish payment.succeeded
              [order-service]  payment.succeeded process
```

The correlation id is still there and still separate: grep any service log for the
`X-Request-Id` value to read the story in words, and use the trace to see the shape and
the timings.


## Roadmap

- [x] Catalog with Flyway-managed schema
- [x] Order placement inside a single transaction
- [x] Split Order and Inventory over HTTP
- [x] Replace the HTTP call with Kafka events
- [x] Saga with compensating transactions
- [x] Idempotent consumers and transactional outbox
- [x] Retries and dead letter queue
- [x] Auth, API gateway, React UI
- [x] Tracing and metrics