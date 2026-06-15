package com.cashflow.api;

import com.cashflow.entity.ExpenseTransaction;
import com.cashflow.service.LedgerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller that exposes the debt-settlement engine over HTTP.
 *
 * <p>All endpoints are rooted at {@code /api/v1/settlements}, following
 * versioned API conventions so future breaking changes can be introduced
 * under {@code /api/v2/...} without disrupting existing clients.</p>
 *
 * <h2>Endpoint Summary</h2>
 * <pre>
 *   POST   /api/v1/settlements/transactions        — ingest a single raw transaction
 *   POST   /api/v1/settlements/transactions/batch  — ingest a batch of transactions
 *   GET    /api/v1/settlements/optimize            — run synchronous optimisation
 *   GET    /api/v1/settlements/optimize/async      — trigger async optimisation
 *   POST   /api/v1/settlements/optimize/group      — async optimisation for a user group
 * </pre>
 *
 * <h2>Error Handling</h2>
 * <p>Domain validation errors (e.g. negative amount, self-transfer) are
 * translated to {@code 400 Bad Request} by a global
 * {@code @ControllerAdvice} (to be implemented). Unexpected exceptions
 * fall through to a {@code 500 Internal Server Error} response.</p>
 *
 * <h2>Thread Model</h2>
 * <p>Synchronous endpoints block the Tomcat thread for the duration of the
 * operation. Async endpoints ({@code /optimize/async}, {@code /optimize/group})
 * delegate to Spring's task executor via {@link LedgerService}'s
 * {@code @Async} methods, returning a {@link CompletableFuture} that Spring
 * MVC unwraps into a deferred HTTP response automatically.</p>
 */
@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    private static final Logger log = LoggerFactory.getLogger(SettlementController.class);

    private final LedgerService ledgerService;

    /**
     * Constructor injection of the service layer dependency.
     *
     * @param ledgerService orchestrates persistence and algorithm invocation
     */
    public SettlementController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    // -------------------------------------------------------------------------
    // Transaction ingestion endpoints
    // -------------------------------------------------------------------------

    /**
     * {@code POST /api/v1/settlements/transactions}
     *
     * <p>Accepts a single raw {@link ExpenseTransaction} in the request body
     * and persists it to the ledger. The transaction is not yet optimised —
     * it represents a raw obligation that will be included in the next
     * optimisation pass.</p>
     *
     * <p><b>Request body example:</b>
     * <pre>{@code
     * {
     *   "amount": 120.50,
     *   "description": "Dinner at Nobu",
     *   "payerId": "a1b2c3d4-...",
     *   "payeeId": "e5f6a7b8-..."
     * }
     * }</pre>
     *
     * @param transaction the expense transaction parsed from the JSON body
     * @return {@code 201 Created} with the saved entity (ID and timestamps populated)
     */
    @PostMapping("/transactions")
    public ResponseEntity<ExpenseTransaction> addTransaction(
            @RequestBody ExpenseTransaction transaction) {

        log.info("POST /transactions — payerId={} payeeId={} amount={}",
                transaction.getPayerId(), transaction.getPayeeId(), transaction.getAmount());

        // TODO: ExpenseTransaction saved = ledgerService.saveTransaction(transaction);
        // TODO: return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(transaction);
    }

    /**
     * {@code POST /api/v1/settlements/transactions/batch}
     *
     * <p>Accepts a JSON array of raw {@link ExpenseTransaction} records and
     * persists them in a single transactional batch. Useful for bulk-importing
     * a trip's receipts from a client app.</p>
     *
     * @param transactions list of expense transactions from the JSON body
     * @return {@code 201 Created} with the list of saved entities
     */
    @PostMapping("/transactions/batch")
    public ResponseEntity<List<ExpenseTransaction>> addTransactions(
            @RequestBody List<ExpenseTransaction> transactions) {

        log.info("POST /transactions/batch — {} transactions received", transactions.size());

        // TODO: List<ExpenseTransaction> saved = ledgerService.saveAllTransactions(transactions);
        // TODO: return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(transactions);
    }

    // -------------------------------------------------------------------------
    // Optimisation endpoints
    // -------------------------------------------------------------------------

    /**
     * {@code GET /api/v1/settlements/optimize}
     *
     * <p>Triggers a <em>synchronous</em> settlement optimisation pass over all
     * unsettled transactions in the ledger. Blocks until the greedy heap
     * algorithm completes and returns the full minimal settlement plan.</p>
     *
     * <p>Suitable for small groups or development/testing. For production
     * workloads with many participants, prefer {@code GET /optimize/async}.</p>
     *
     * @return {@code 200 OK} with the minimal list of synthetic settlement transactions
     */
    @GetMapping("/optimize")
    public ResponseEntity<List<ExpenseTransaction>> optimize() {

        log.info("GET /optimize — running synchronous settlement pass");

        // TODO: List<ExpenseTransaction> result = ledgerService.optimizeDebts();
        // TODO: return ResponseEntity.ok(result);
        return ResponseEntity.ok(List.of());
    }

    /**
     * {@code GET /api/v1/settlements/optimize/async}
     *
     * <p>Triggers an <em>asynchronous</em> settlement optimisation pass.
     * The HTTP thread is released immediately; Spring MVC resolves the
     * {@link CompletableFuture} into a deferred response once the algorithm
     * completes on the background executor thread.</p>
     *
     * <p>Clients should be prepared to wait (or poll a status endpoint)
     * since response time depends on group size and executor queue depth.</p>
     *
     * @return a {@link CompletableFuture} wrapping {@code 200 OK} and the
     *         settlement list; resolved asynchronously
     */
    @GetMapping("/optimize/async")
    public CompletableFuture<ResponseEntity<List<ExpenseTransaction>>> optimizeAsync() {

        log.info("GET /optimize/async — dispatching async settlement pass");

        // TODO: return ledgerService.optimizeDebtsAsync()
        //           .thenApply(ResponseEntity::ok);
        return CompletableFuture.completedFuture(ResponseEntity.ok(List.of()));
    }

    /**
     * {@code POST /api/v1/settlements/optimize/group}
     *
     * <p>Triggers an <em>asynchronous</em> optimisation scoped to a specific
     * group of participants (e.g. a trip or household). Only transactions
     * where both payer and payee are in the supplied {@code userIds} list are
     * included, isolating the result from unrelated ledger activity.</p>
     *
     * <p><b>Request body example:</b>
     * <pre>{@code
     * ["a1b2c3d4-...", "e5f6a7b8-...", "c9d0e1f2-..."]
     * }</pre>
     *
     * @param userIds JSON array of participant UUIDs defining the settlement group
     * @return a {@link CompletableFuture} wrapping {@code 200 OK} and the
     *         scoped settlement list
     */
    @PostMapping("/optimize/group")
    public CompletableFuture<ResponseEntity<List<ExpenseTransaction>>> optimizeForGroup(
            @RequestBody List<UUID> userIds) {

        log.info("POST /optimize/group — {} participants in group", userIds.size());

        // TODO: return ledgerService.optimizeDebtsForGroup(userIds)
        //           .thenApply(ResponseEntity::ok);
        return CompletableFuture.completedFuture(ResponseEntity.ok(List.of()));
    }
}
