# cashflow-settlement-engine

A microservice that ingests raw expense data from a group of participants and computes the minimum set of peer-to-peer payment paths required to settle all outstanding obligations.

---

## Architecture

The service is built on Spring Boot 3.x and organized into four layers:

- **API** — REST controllers (`/api/v1/settlements`) handle transaction ingestion and trigger settlement runs.
- **Service** — `LedgerService` coordinates persistence and algorithm dispatch. Long-running optimization passes are offloaded to Spring's managed thread pool via `@Async`, returning `CompletableFuture` to the caller.
- **Engine** — The `SettlementAlgorithm` interface decouples the algorithm from the rest of the application. `GreedyHeapSettlementStrategy` is the default implementation.
- **Persistence** — Spring Data JPA with a PostgreSQL backend. `ExpenseTransaction` is the primary entity; UUIDs are used as primary keys to allow client-side ID generation.

---

## The Math

### Data Model

The input is modeled as a directed weighted graph `G = (V, E)` where:

- Each vertex `v ∈ V` represents a participant (identified by UUID).
- Each directed edge `e = (payer, payee, amount)` represents a single outstanding monetary obligation.

A naive settlement would require settling each of the `|E|` edges individually, which produces up to `O(N²)` payment legs for a dense group of `N` participants.

### Net Balance Aggregation — O(M)

The first pass collapses the edge set into a single net balance per vertex:

```
netBalance[v] = Σ(amount of all inbound edges to v)
              − Σ(amount of all outbound edges from v)
```

A positive `netBalance` identifies a creditor. A negative `netBalance` identifies a debtor. A zero value means the participant is already square and is dropped from further consideration. This reduces the problem from `M` raw edges to at most `N` signed scalar values.

### Heap-Based Greedy Cancellation — O(N log N)

Two priority queues are constructed from the net balance array:

- **Min-heap (`debtorHeap`)** — ordered by ascending `netBalance`. The head always holds the largest debtor (most-negative value).
- **Max-heap (`creditorHeap`)** — ordered by descending `netBalance`. The head always holds the largest creditor (most-positive value).

The algorithm then executes a greedy cancellation loop:

```
while debtorHeap and creditorHeap are non-empty:
    D = poll(debtorHeap)      // largest debtor
    C = poll(creditorHeap)    // largest creditor
    settlement = min(|D.netBalance|, C.netBalance)
    emit transaction: D → C, amount = settlement
    D.netBalance += settlement
    C.netBalance -= settlement
    if D.netBalance != 0: push D back into debtorHeap
    if C.netBalance != 0: push C back into creditorHeap
```

Each iteration emits exactly one payment and zeroes at least one participant, so the loop executes at most `N − 1` times. Each heap poll and re-insertion is `O(log N)`.

### Complexity Summary

| Phase                   | Time           | Space  |
|-------------------------|----------------|--------|
| Net balance aggregation | O(M)           | O(N)   |
| Heap construction       | O(N log N)     | O(N)   |
| Greedy cancellation     | O(N log N)     | O(N)   |
| **Total**               | **O(M + N log N)** | **O(N)** |

The output is guaranteed to contain no more than `N − 1` settlement transactions, which is the theoretical minimum for any algorithm that permits fractional splits.

> **Note:** This guarantee does not hold under integer (no-split) constraints. That variant of the problem is NP-hard and requires a different approach.

---

## Local Setup

**Prerequisites:** Java 21, Maven 3.9+, PostgreSQL 15+.

Create the database and configure `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/cashflow
spring.datasource.username=your_user
spring.datasource.password=your_password
spring.jpa.hibernate.ddl-auto=update
spring.task.execution.pool.core-size=4
spring.task.execution.pool.max-size=16
```

Run the application:

```bash
mvn clean install
mvn spring-boot:run
```

Or build and run the JAR directly:

```bash
java -jar target/cashflow-settlement-engine-*.jar
```

The service starts on port `8080` by default.

---

## API Reference

### Ingest a transaction

```
POST /api/v1/settlements/transactions
Content-Type: application/json

{
  "amount": 120.50,
  "description": "Dinner — 14 Jun",
  "payerId": "a1b2c3d4-e5f6-...",
  "payeeId": "b2c3d4e5-f6a7-..."
}
```

### Run synchronous optimization

```
GET /api/v1/settlements/optimize
```

Returns the minimal settlement list. Blocks until complete.

### Run asynchronous optimization

```
GET /api/v1/settlements/optimize/async
```

Dispatches the optimization run to the background executor. Response is deferred.

### Run scoped optimization for a group

```
POST /api/v1/settlements/optimize/group
Content-Type: application/json

["a1b2c3d4-...", "b2c3d4e5-...", "c3d4e5f6-..."]
```

Restricts the optimization pass to transactions where both payer and payee are in the supplied UUID list.

---

## Project Structure

```
src/main/java/com/cashflow/
├── api/
│   └── SettlementController.java
├── engine/
│   ├── SettlementAlgorithm.java
│   └── GreedyHeapSettlementStrategy.java
├── entity/
│   ├── ExpenseTransaction.java
│   └── UserNetBalance.java
├── repository/
│   └── TransactionRepository.java
└── service/
    └── LedgerService.java
```
