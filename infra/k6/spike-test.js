// Optional load benchmark, separate from the frontend's "simulate spike" button
// (which demonstrates correctness under concurrency, not throughput at scale).
// Run against a live deployment to get a concrete number for the README, e.g.:
//
//   BASE_URL=https://your-subdomain.duckdns.org k6 run infra/k6/spike-test.js
//
// Requires https://k6.io/ and an admin-reset call beforehand so stock is full.
import http from "k6/http";
import { check } from "k6";
import { randomString } from "https://jslib.k6.io/k6-utils/1.2.0/index.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:18080";
const SKU = "FLASH-SALE-ITEM";

export const options = {
  scenarios: {
    checkout_spike: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "5s", target: 50 },
        { duration: "10s", target: 300 },
        { duration: "5s", target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"], // transport-level failures only; 409 SOLD_OUT is a correct response, not a failure
  },
};

export default function () {
  const idempotencyKey = `k6-${randomString(16)}`;
  const res = http.post(
    `${BASE_URL}/api/checkout`,
    JSON.stringify({ sku: SKU, userId: `k6-user-${__VU}-${__ITER}`, quantity: 1 }),
    { headers: { "Content-Type": "application/json", "Idempotency-Key": idempotencyKey } }
  );
  check(res, {
    "status is 202, 409, or 429": (r) => [202, 409, 429].includes(r.status),
  });
}
