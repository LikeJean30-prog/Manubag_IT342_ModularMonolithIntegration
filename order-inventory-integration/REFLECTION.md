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


# REFLECTION ANSERS FOR LAB 4 - Tiangge Marketplace: Run Your Shop Unattended 
## Marketplace (Tiangge) Reflection Questions

## 1. TG-X8PJDU: why my stock figure and Tiangge's disagreed
 
My stock figure is the `stock` column of the Supabase `inventory` table, which only `InventoryServiceImpl.reserve()` and `restock()` change; it is never copied from Tiangge. Right after the restart at 08:55:54 the app found the delivery of RO-cb6c91a1 and restocked 20 units of P100 (`Restocked 20 unit(s) of P100 from supplier order 56`, 08:55:56), then resolved three backorders against that stock, including TG-X8PJDU (7 x P100 and 7 x P300, local order 157) at 08:56:02. Tiangge only knows what I publish plus the events it sees, and my first stock publish after the restart was late because the heartbeat on the main thread was timing out (08:55:59), so it still held the old P100 figure of 10 and had worked out 4 itself. The ACCEPTED resolution was sent at 08:56:05, before my first `Stock sent to Tiangge` line at 08:56:06 (P100=3), so my database was ahead of what Tiangge had been told, not wrong. I fixed the ordering: `BackorderResolver.onDelivery` now calls `StockSync.publishNow(productId)` before it resolves any backorder, and `OutboxSender.drain` sends nothing until `stockSync.isInitialised()` is true after the first full stock publish.
 
## 2. Event evt_10db8a018501b856 delivered twice
 
Every event ID is stored in the `processed_event` table (entity `ProcessedEvent`, `@Id String eventId`), and `FeedProcessor.processEvent` starts with `if (processedRepository.existsById(event.eventId())) { return; }`. The first delivery is handled in one `TransactionTemplate` transaction that saves the local order, the `tiangge_orders` row (primary key is the Tiangge order ID, here TG-PQLH9S, which became local order 3), the decision in `tiangge_outbox`, and `processedRepository.markProcessed(eventId)`, so they commit together or not at all. When seq 2 arrives with the same event ID, `existsById` is true and nothing happens, and even a new event ID for the same order would be stopped by `tianggeOrders.existsById(tianggeId)`. The log shows `Tiangge order TG-PQLH9S -> local order 3 -> ACCEPTED` once, and `/verify` showed 18 repeated deliveries and 0 processed twice. If the app restarted between the two deliveries, `FeedPoller` resumes from the numeric cursor stored in `feed_cursor`: if the crash came before the transaction committed, the event is processed once after the restart; if it came after, seq 1 is already in `processed_event`, so a re-read or seq 2 is skipped, and the decision still waiting in the outbox is sent by `OutboxSender`.
 
## 3. TG-XEZAYR: from delivery to accepted
 
PO-104119 is my supplier order RO-f0f4c7e2 (1 case of QEX-7491, product P300), submitted at 16:26:33 through the LegacySupply adapter. `DeliveryTrackingScheduler` polls every open supplier order; at 16:32:39 it saw status DELIVERED, saved it and published `SupplierOrderDeliveredEvent(P300, 1, 36)`. `InventoryReplenishmentListener` in the Inventory module handled that event and called `inventoryService.restock("P300", 1)` (`Restocked 1 unit(s) of P300 from supplier order 36`, 16:32:39), so Inventory never learns about Tiangge. After that committed, `BackorderResolver.onDelivery` ran `resolveReady()`: for each BACKORDERED order, oldest first, it checked `hasOpenReorder` for the order's products and called `OrderService.resolveBackorder(42)`, which reserved the stock, set the order CONFIRMED and queued a RESOLUTION ACCEPTED row (`Backorder TG-XEZAYR (local order 42) resolved after supplier delivery -> ACCEPTED`, 16:32:41). `OutboxSender` delivered it: the first call at about 16:32:59 timed out on my side but Tiangge recorded it, and the retry went through at 16:33:05 (`Sent RESOLUTION ACCEPTED ... 24713 ms after it was decided`), which is safe because resolutions can be retried.