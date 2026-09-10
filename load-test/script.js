import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";

// Restaurants placing orders through the gateway: log in, browse the catalog, place an
// order. Every request in this script goes through the gateway on 8080, the same single
// entry point a real client would use.
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

// One restaurant per VU, so the gateway's per-identity rate limit (5 req/s, burst 10) is
// never the thing being measured — each VU paces itself well under that on its own, the
// way one real restaurant would, and the aggregate load comes from having many of them.
const RESTAURANT_COUNT = 30;

// Informational only, not a pass/fail signal: the saga is asynchronous, and the outbox
// poller alone adds up to ~1s per hop, so seeing this be high at a short check delay
// says "the saga usually takes longer than that to settle", not "orders are failing".
// orqentra_saga_outcome_total on the order service's own /actuator/prometheus is the
// authoritative count of how many orders actually reached a terminal state.
const stillUnsettledAfterOneCheck = new Counter("still_unsettled_after_one_check");
const orderPlaceDuration = new Trend("order_place_duration", true);

export const options = {
  scenarios: {
    restaurants: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "30s", target: RESTAURANT_COUNT }, // ramp up
        { duration: "60s", target: RESTAURANT_COUNT }, // hold
        { duration: "15s", target: 0 },                // ramp down
      ],
      gracefulRampDown: "10s",
    },
  },
  thresholds: {
    // The test fails, rather than merely reporting a number, if either of these slips.
    http_req_failed: ["rate<0.01"],
    "http_req_duration{name:place_order}": ["p(95)<800"],
    "http_req_duration{name:get_products}": ["p(95)<500"],
    "http_req_duration{name:get_stock}": ["p(95)<500"],
  },
};

/**
 * Registers and logs in one restaurant per VU, once, before the load stages begin.
 * Sequenced with a short pause between each: registration and login are unauthenticated
 * and therefore rate-limited by client IP, and every VU here shares this machine's one
 * IP address, so creating many accounts at once would just spend the whole pool on 429s
 * before a single order is ever placed.
 */
export function setup() {
  const tokens = [];
  for (let i = 0; i < RESTAURANT_COUNT; i++) {
    const email = `load-test-${Date.now()}-${i}@orqentra.test`;
    const password = "load-test-pass";

    const registerRes = http.post(
      `${BASE_URL}/api/auth/register`,
      JSON.stringify({ email, password, restaurantName: `Load Test Kitchen ${i}` }),
      { headers: { "Content-Type": "application/json" }, tags: { name: "register" } },
    );
    check(registerRes, { "registered": (r) => r.status === 201 });

    const loginRes = http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email, password }),
      { headers: { "Content-Type": "application/json" }, tags: { name: "login" } },
    );
    check(loginRes, { "logged in": (r) => r.status === 200 });
    tokens.push(JSON.parse(loginRes.body).token);

    sleep(0.3); // ~3.3 req/s of registration+login traffic, under the IP-keyed limit
  }
  return { tokens };
}

export default function (data) {
  const token = data.tokens[__VU % data.tokens.length];
  const headers = { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };

  const products = http.get(`${BASE_URL}/api/products`, {
    headers, tags: { name: "get_products" },
  });
  check(products, { "catalog 200": (r) => r.status === 200 });

  const stock = http.get(`${BASE_URL}/api/inventory/SKU-LOAD-001`, {
    headers, tags: { name: "get_stock" },
  });
  check(stock, { "stock 200": (r) => r.status === 200 });

  const placed = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify({ items: [{ sku: "SKU-LOAD-001", quantity: 1 }] }),
    { headers, tags: { name: "place_order" } },
  );
  const placedOk = check(placed, { "order 201 PENDING": (r) => r.status === 201 });
  orderPlaceDuration.add(placed.timings.duration);

  if (placedOk) {
    const reference = JSON.parse(placed.body).reference;

    // One follow-up read to see whether the saga has settled by the time this VU gets
    // back to it — not a tight poll loop, since that would just be additional load
    // rather than a realistic client checking back on its own order.
    sleep(1.5);
    const settled = http.get(`${BASE_URL}/api/orders/${reference}`, {
      headers, tags: { name: "get_order" },
    });
    const status = settled.status === 200 ? JSON.parse(settled.body).status : null;
    if (status !== "CONFIRMED") {
      stillUnsettledAfterOneCheck.add(1);
    }
  }

  sleep(1 + Math.random()); // keeps each VU comfortably under the per-account rate limit
}
