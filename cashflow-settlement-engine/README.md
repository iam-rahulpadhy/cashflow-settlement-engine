# cashflow-settlement-engine

A Spring Boot microservice that ingests raw expense records from a group of participants and computes the **minimum number of payments** needed to settle all debts — the core algorithm behind apps like Splitwise.

---

## How the algorithm works

Debt between N people is modeled as a directed weighted graph. A naive settlement touches every edge individually — up to O(N²) payments. This engine reduces that to **at most N−1 payments** in **O(N log N)** time using two heaps.

**Step 1 — Net balance aggregation (O(M)):**  
For each raw transaction, add the amount to the payee's balance and subtract it from the payer's. After one pass, every participant has a single signed net value: positive = creditor, negative = debtor, zero = already square.

**Step 2 — Greedy cancellation (O(N log N)):**  
Put debtors in a min-heap (largest debt at head) and creditors in a max-heap (largest credit at head). Each iteration pairs the biggest debtor with the biggest creditor, settles `min(|debt|, credit)` in one payment, and zeroes out at least one of them. The loop runs at most N−1 times.

```
while heaps non-empty:
    D = largest debtor,  C = largest creditor
    pay = min(|D.balance|, C.balance)
    emit: D → C, amount = pay
    adjust balances, re-insert if non-zero
```

| Phase                 | Time           | Space |
|-----------------------|----------------|-------|
| Balance aggregation   | O(M)           | O(N)  |
| Heap construction     | O(N log N)     | O(N)  |
| Greedy cancellation   | O(N log N)     | O(N)  |
| **Total**             | **O(M + N log N)** | **O(N)** |

---

## Tech stack

- Java 21, Spring Boot 3.x
- Spring Data JPA + PostgreSQL
- `@Async` + `CompletableFuture` for non-blocking settlement runs
- H2 in-memory DB for local testing (no Postgres needed)

---

## Project structure

```
src/main/java/com/cashflow/
├── api/
│   ├── SettlementController.java     # REST endpoints
│   └── GlobalExceptionHandler.java   # translates exceptions to JSON errors
├── engine/
│   ├── SettlementAlgorithm.java      # strategy interface
│   └── GreedyHeapSettlementStrategy.java  # min/max-heap implementation
├── entity/
│   ├── ExpenseTransaction.java       # JPA entity (the primary table)
│   └── UserNetBalance.java           # transient POJO used by the algorithm
├── repository/
│   └── TransactionRepository.java
├── runner/
│   └── TestRunner.java               # proof harness (h2test profile)
└── service/
    └── LedgerService.java
```

---

## Running locally

**With H2 (no Postgres needed):**
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2test
```
Prints the algorithm proof on startup — 11 raw edges collapsed to 4 settlements (≤ V−1 = 5).

**With PostgreSQL:**

Create a database named `cashflow_db`, then configure credentials in `src/main/resources/application.yml` (or via env vars `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`):

```bash
./mvnw spring-boot:run
```

---

## API

```
POST   /api/v1/settlements/transactions     # ingest one expense record
GET    /api/v1/settlements/optimize         # run settlement (sync)
GET    /api/v1/settlements/optimize/async   # run settlement (async, non-blocking)
```

**Example — add a transaction:**
```json
POST /api/v1/settlements/transactions
{
  "amount": 120.50,
  "description": "Dinner",
  "payerId": "aaaaaaaa-0000-0000-0000-000000000001",
  "payeeId": "bbbbbbbb-0000-0000-0000-000000000002"
}
```
