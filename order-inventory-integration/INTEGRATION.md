# LegacySupply Integration Documentation

**Client ID:** 23-3870-600  
**Package:** `edu.cit.manubag.supplier`  
**Base URL:** `https://legacysupply.onrender.com/api/v1`

---

## 1. Product Mapping Table

LegacySupply operates strictly in wholesale **CASE** units, whereas our internal store inventory tracks single **UNIT** items. The Anti-Corruption Layer (ACL) maps internal product IDs to LegacySupply SKUs and handles the mathematical unit-to-case conversion.

| Internal Product ID | Product Name | LegacySupply `SupplierSku` | `PackSize` (Units/Case) |
| :--- | :--- | :--- | :--- |
| `P100` | Wireless Mouse | `LS-WGT-A1` | 10 |
| `P200` | Mechanical Keyboard | `LS-SPR-B2` | 24 |
| `P300` | USB-C Hub | `LS-BLT-G3` | 100 |

---

## 2. LegacySupply Session Behavior & Lifetime Measurement

- **Authentication Endpoint:** `POST /api/v1/auth/login`
- **Request Format:** XML payload containing `<ClientId>` and `<ApiKey>`.
- **Response Format:** XML payload returning `<SessionToken>` and `<ExpiresInSeconds>`.

### Session Lifetime Measurement:
Through endpoint probing via Postman/curl and automated tracking:
- **Returned `ExpiresInSeconds`:** LegacySupply reports an expiration window of **300 seconds (5 minutes)**.
- **Actual Measured Expiry:** Sessions remain strictly valid for **300 seconds**. Any request sent with a session token past 300 seconds yields an `HTTP 401 Unauthorized` (`INVALID_SESSION` / `EXPIRED_TOKEN`).
- **ACL Handling Strategy:** The `LegacySupplySession` component automatically proactively refreshes the session token 30 seconds prior to expiration or re-authenticates automatically if an HTTP 401 response is intercepted.

---

## 3. Error Codes & Root Causes

During API discovery and chaos testing, the following LegacySupply HTTP status and XML error codes were observed and handled:

| HTTP Status | Error Code / Exception | Cause / Trigger |
| :--- | :--- | :--- |
| `400 Bad Request` | `INVALID_XML` | Malformed XML structure, missing required tags, or invalid UOM. |
| `401 Unauthorized` | `INVALID_CREDENTIALS` | Invalid Client ID or API Key provided in the `/auth/login` body. |
| `401 Unauthorized` | `EXPIRED_TOKEN` | Request made using a session token older than 300 seconds. |
| `404 Not Found` | `ORDER_NOT_FOUND` | Querying `/api/v1/orders/{poNumber}` for a non-existent PO number. |
| `429 Too Many Requests` | `RATE_LIMIT_EXCEEDED` | Exceeded the max request quota within a 1-minute sliding window. |
| `500 Internal Error` | `SUPPLIER_FAULT` | LegacySupply backend failure or simulated service outage (chaos testing). |
| `503 Service Unavailable` | `SERVICE_DOWN` | Server temporary outage. Request remains `PENDING` for retry. |

---

## 4. `Qty` and `Uom` Explanation & Worked Example

### Definition in Our Words:
- **`Uom` (Unit of Measure):** LegacySupply requires purchase orders to specify `CASE`. Ordering single item units directly is not supported by the external API.
- **`Qty` (Quantity):** Represents the number of **full cases** requested from the supplier, not the individual item count.

### Case Calculation Formula:
$$\text{Cases To Order} = \left\lceil \frac{\text{Units Needed}}{\text{Pack Size}} \right\rceil$$

### Worked Example:
- **Scenario:** `P100` (Wireless Mouse) stock drops below the threshold, requiring **25 units**.
- **Lookup:** `P100` maps to SKU `LS-WGT-A1` with a `PackSize` of **10 units/case**.
- **Calculation:** $\lceil 25 / 10 \rceil = \lceil 2.5 \rceil = 3 \text{ cases}$.
- **LegacySupply Payload:**
  ```xml
  <PurchaseOrder>
      <BuyerRef>RO-A1B2C3D4</BuyerRef>
      <SupplierSku>LS-WGT-A1</SupplierSku>
      <Qty>3</Qty>
      <Uom>CASE</Uom>
  </PurchaseOrder>