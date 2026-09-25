# Reflection Answers

### Question 1
**At 19:45:56 your request for BuyerRef "23-3870-600-P300-001" (X-Request-Id 23-3870-600-P300-001) received a 503, but LegacySupply had already created PO-100053. Walk through exactly what your adapter did next, and explain why that did or did not result in a second order.**

**Answer:**
When the `503 Service Unavailable` response was received at 19:45:56, our `SupplierGatewayImpl` adapter intercepted the failure and caught the exception. Instead of discarding the reorder or throwing an unhandled exception, the adapter kept the reorder record saved in the local database in a `PENDING` status with the fixed `request_id` (`23-3870-600-P300-001`) and `buyer_ref` (`23-3870-600-P300-001`). When the background scheduler (`PendingOrdersScheduler`) subsequently retried sending the request, it re-sent the payload reusing the exact same `X-Request-Id` and `BuyerRef` values. Because LegacySupply checks for existing orders by `X-Request-Id` / `BuyerRef` idempotently, the server recognized the previous execution that created `PO-100053` and safely returned the existing order details without creating a duplicate purchase order.

---

### Question 2
**LegacySupply never tells you how long a session lasts. Measure your session lifetime from your own logs, state the number, and explain how your adapter decides when to sign in again.**

**Answer:**
Based on our request logs (such as the 401 `E-AUTH-07 token expired` error at 19:28:55 following a login at 19:23:41), the session lifetime is measured to be exactly **300 seconds (5 minutes)**. Our adapter manages authentication via a dedicated `LegacySupplySession` component that tracks the login timestamp and active `SessionToken`. It decides to sign in again under two specific scenarios: proactively when a cached token is within 30 seconds of its 300-second expiration window, and reactively whenever an outgoing HTTP API request receives an `HTTP 401 Unauthorized` response. When a 401 is encountered, the adapter invalidates the current token, executes a new login call (`POST /api/v1/auth/token`), acquires a fresh token, and transparently retries the original API call.

---

### Question 3
**The catalog reports PackSize and orders report Uom "CS". Using one of your own orders, show the arithmetic from "units your Inventory needed" to the Qty you sent, and to the units your Inventory received on delivery.**

**Answer:**
For order `PO-100053` (`BuyerRef: 23-3870-600-P300-001`), our inventory required restocking for product `P300` (USB-C Hub). According to our catalog mapping table, `P300` corresponds to supplier SKU `QEX-7491` with a `PackSize` of **100 units per case**. When customer orders depleted our stock, the inventory needed **150 units**. The adapter calculated the order case quantity using ceiling division: $\lceil 150 / 100 \rceil = \lceil 1.5 \rceil = 2 \text{ cases}$ (`Qty = 2`, `Uom = CS`). When LegacySupply fulfilled and delivered `PO-100053`, the delivery listener calculated total received units by multiplying cases by pack size ($2 \text{ cases} \times 100 \text{ units/case} = 200 \text{ units}$), restocking our inventory with exactly **200 units**.