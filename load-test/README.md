# Load test

`script.js` is a [k6](https://k6.io) script simulating restaurants placing orders through
the gateway: register, log in, browse the catalog, check stock, place an order, then look
back at it once. It ramps to 30 concurrent virtual restaurants, holds, and ramps back
down.

## Why 30 VUs, and why each is its own account

The gateway rate-limits by authenticated identity (5 req/s, burst 10) and falls back to
client IP for the unauthenticated `/api/auth/**` routes. Every VU on this script shares
one machine's IP address, so:

- `setup()` registers and logs in all 30 accounts **once**, sequentially, with a short
  pause between each, so registration/login traffic never bursts past the IP-keyed limit.
- Each VU then keeps its own token for the whole run and paces its own requests well
  under the per-identity limit, the way one real restaurant would.

Aggregate load comes from having many independently-paced restaurants, not from any one
of them being throttled.

## Seeded stock

`SKU-LOAD-001` is seeded with 1,000,000 units (`inventory-service` migration `V5`) and
priced at 500.00 (`order-service` migration `V6`, comfortably under the payment decline
threshold). The script only orders this SKU, so the run measures the confirmed-order path
— the thing an ordinary customer does — rather than mixing in the stock-rejection or
payment-decline sagas, which are already covered by the automated test suite.

## Running it

Bring the whole stack up first (`docker compose up -d`, then start all six services), then:

```bash
docker run --rm -i -v "$(pwd):/scripts" \
  -e BASE_URL=http://host.docker.internal:8080 \
  grafana/k6 run /scripts/script.js
```

`host.docker.internal` is what lets a container reach services running natively on the
host, which is how this was actually run — `k6` itself needed no other setup. On Linux,
use `--network host` and drop the `BASE_URL` override instead.

The script fails outright (`k6` exits non-zero) if `http_req_failed` rate exceeds 1%, or
if the p95 latency for placing an order, browsing the catalog, or checking stock exceeds
its threshold — see `options.thresholds` in the script. These are not just numbers in a
report; a regression trips the same exit code a CI step would look for.

## Results

Measured on one development laptop — **13th Gen Intel Core i5-1335U, 16 GB RAM, Windows
11** — running all six Orqentra services, four Postgres containers, Redpanda, Redis, and
the Prometheus/Grafana/Jaeger observability stack simultaneously, with `k6` itself also
running locally in Docker. This is a single-machine measurement, not a production
benchmark, and it says nothing about behaviour on real infrastructure with the load
generator on separate hardware from the system under test.

**Shape:** ramp 0→30 VUs over 30s, hold 30 VUs for 60s, ramp down over 15s (2m16s wall
clock including graceful stop).

**HTTP, synchronous, through the gateway** — 3,248 requests, 23.7 req/s, **0% failed**:

| Request | avg | p90 | p95 | max |
|---|---|---|---|---|
| `GET /api/products` | 41.7ms | 56.6ms | 69.0ms | — |
| `GET /api/inventory/{sku}` | 36.4ms | 53.9ms | 63.8ms | — |
| `POST /api/orders` | 50.2ms | 69.3ms | 79.7ms | 1.02s |
| all requests | 44.6ms | 63.1ms | 78.3ms | 1.12s |

All three latency thresholds and the error-rate threshold passed.

**The saga, asynchronous** — 797 orders placed, **100% eventually reached `CONFIRMED`**
(read from `orqentra_saga_outcome_total` and `orqentra_saga_duration_seconds`, the metrics
built in an earlier phase specifically to answer this question):

- Average time from order accepted to `CONFIRMED`: **~3.17s**
  (`orqentra_saga_duration_seconds_sum / _count` = 2528.4s / 797).
- Peak Kafka consumer lag during the run: **6 messages**
  (`inventory-service` on `order.created`); every other consumer group stayed at 3 or
  below, and all groups drained back to 0 within seconds of the ramp-down finishing.
- Peak outbox depth during the run: **17 unpublished rows** (`payment-service`),
  15 each for `inventory-service` and `order-service`; all three drained to 0 shortly
  after the hold phase ended.

## Where it starts to degrade

Nothing broke at this concurrency: 0% HTTP errors, every order eventually confirmed, and
every queue fully drained. The interesting number is not a failure — it is that **~3
seconds** average saga latency against **sub-80ms** HTTP latency. The gap is the outbox
poller's own `fixedDelay=1000` schedule, paid roughly once per hop across the three
sequential hops a confirmed order takes (order → stock reservation → payment → order
confirmed). A client polling `GET /api/orders/{reference}` faster than about once every
2 seconds will routinely see `PENDING` or `AWAITING_PAYMENT` rather than the final state —
which is exactly why the notification service's SSE stream exists, so a client is told the
moment a transition happens instead of guessing how often to poll.

The first thing to tune before pushing concurrency further would be that poll interval —
or accepting that saga latency, not HTTP throughput, is this architecture's real ceiling
at higher order rates on a single broker partition per topic.
