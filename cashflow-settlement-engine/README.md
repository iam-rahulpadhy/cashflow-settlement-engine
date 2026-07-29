# Cash Flow Settlement Engine

A REST API that figures out the **minimum number of payments** needed to settle debts in a group — basically the backend logic of something like Splitwise.

The interesting part is the algorithm. If 6 people go on a trip and pay for different things, you can easily end up with 10+ debt relationships. This engine collapses all of that down to at most **N−1 payments** (where N is the number of people), which is the theoretical minimum.

---

## How it works — the big picture

```mermaid
flowchart TD
    Client(["Client (Postman / App)"])
    SF["Spring Security\n(HTTP Basic Auth)"]
    SC["SettlementController"]
    LS["LedgerService"]
    ASYNC["@Async Thread Pool\nsettlement-async-*"]
    ALGO["GreedyHeapSettlementStrategy\n(min/max-heap algorithm)"]
    DB[("PostgreSQL")]

    Client -->|"POST /transactions"| SF
    Client -->|"GET /optimize"| SF
    Client -->|"GET /optimize/async"| SF
    SF -->|"401 if unauthenticated"| Client
    SF -->|"authenticated"| SC

    SC -->|"save transaction"| LS
    SC -->|"sync settle"| LS
    SC -->|"async settle"| LS

    LS -->|"persist"| DB
    LS -->|"findBySettledFalse"| DB
    LS -->|"dispatches via @Async"| ASYNC
    ASYNC --> ALGO
    LS --> ALGO
    ALGO -->|"minimal settlement list"| LS
    LS -->|"mark settled + save results"| DB
    DB -->|"JSON response"| Client
```

---

## The algorithm — step by step

The core problem: given a messy web of who-owes-who, find the simplest way to clear everything.

```mermaid
flowchart TD
    A["Raw Transactions\ne.g. Alice owes Bob $30, Bob owes Charlie $20 ..."]
    B["Net Balance per Person\nOne pass over all transactions — O(M)"]
    POS["Positive balance → Creditor\nsomeone owes them money"]
    NEG["Negative balance → Debtor\nthey owe money"]
    ZERO["Zero balance → skip\nalready square"]
    MAXH["Max-Heap (Creditors)\nlargest creditor at top"]
    MINH["Min-Heap (Debtors)\nlargest debtor at top"]
    LOOP{"Both heaps\nnon-empty?"}
    PAIR["Pair: biggest debtor D with biggest creditor C"]
    SETTLE["settlement = min(|D's debt|, C's credit)\nemit payment: D → C"]
    ADJ["Adjust both balances\nre-insert into heap if non-zero"]
    DONE["Done\n≤ N−1 total payments — O(N log N)"]

    A --> B
    B --> POS --> MAXH
    B --> NEG --> MINH
    B --> ZERO
    MAXH --> LOOP
    MINH --> LOOP
    LOOP -->|yes| PAIR --> SETTLE --> ADJ --> LOOP
    LOOP -->|no| DONE
```

**Why N−1 is the minimum:** you need at least one transaction per person to bring them to zero. With N people, that's N−1 edges in a spanning-tree sense. The heap approach guarantees this bound.

**Complexity:**
| Phase | Time |
|---|---|
| Balance aggregation | O(M) |
| Heap operations | O(N log N) |
| **Total** | **O(M + N log N)** |

---

## Tech stack

- Java 21 + Spring Boot 3
- Spring Security — HTTP Basic Auth
- Spring Data JPA + Hibernate + PostgreSQL
- `@Async` + `CompletableFuture` — settlement runs on a separate thread pool so the HTTP thread isn't blocked
- H2 in-memory DB for running without PostgreSQL

---

## Project structure

```
src/main/java/com/cashflow/
├── api/
│   ├── SettlementController.java          # 3 REST endpoints
│   └── GlobalExceptionHandler.java        # JSON error responses
├── config/
│   └── SecurityConfig.java                # Basic Auth, CSRF off
├── engine/
│   ├── SettlementAlgorithm.java           # interface (strategy pattern)
│   └── GreedyHeapSettlementStrategy.java  # the actual heap algorithm
├── entity/
│   ├── ExpenseTransaction.java            # main JPA table
│   └── UserNetBalance.java                # helper POJO for the algorithm
├── repository/
│   └── TransactionRepository.java
├── runner/
│   └── TestRunner.java                    # self-contained proof (h2test profile)
└── service/
    └── LedgerService.java
```

---

## Running it

**No PostgreSQL (H2 in-memory):**
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2test
```
Boots up, runs the algorithm on a hardcoded 6-person scenario, and prints the proof:
```
11 raw transactions → 4 settlements  (≤ V−1 = 5)  PASS
```

**With PostgreSQL:**
```bash
# one-time setup
sudo -u postgres psql -c "ALTER USER postgres PASSWORD 'postgres';"
sudo -u postgres psql -c "CREATE DATABASE cashflow_db;"

./mvnw spring-boot:run
```

---

## API

All endpoints need **Basic Auth** — default credentials: `admin` / `admin`.

```
POST  /api/v1/settlements/transactions    — add a debt record
GET   /api/v1/settlements/optimize        — run settlement (blocks until done)
GET   /api/v1/settlements/optimize/async  — run settlement (returns immediately)
```

**Quick example:**
```bash
curl -u admin:admin -X POST http://localhost:8080/api/v1/settlements/transactions \
  -H "Content-Type: application/json" \
  -d '{"amount": 50.00, "description": "Dinner", "payerId": "aaaaaaaa-0000-0000-0000-000000000001", "payeeId": "bbbbbbbb-0000-0000-0000-000000000002"}'
```
