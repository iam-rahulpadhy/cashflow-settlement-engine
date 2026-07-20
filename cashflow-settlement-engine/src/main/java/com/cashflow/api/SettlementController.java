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
 * REST controller exposing the cash-flow settlement engine over HTTP.
 *
 * <p>All routes are versioned under /api/v1/settlements so future breaking
 * changes can live under /api/v2/... without disrupting existing clients.</p>
 *
 * <h2>Thread model</h2>
 * <p>Synchronous endpoints (POST /transactions, GET /optimize) hold the
 * Tomcat thread for the duration. Async endpoints (/optimize/async,
 * /optimize/group) delegate to LedgerService's @Async methods, which
 * run on the "settlement-async-" thread pool. Spring MVC unwraps the
 * returned CompletableFuture into a deferred HTTP response automatically,
 * so the I/O thread is released the moment the future is handed back.</p>
 */
@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    private static final Logger log = LoggerFactory.getLogger(SettlementController.class);

    private final LedgerService ledgerService;

    public SettlementController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    // -------------------------------------------------------------------------
    // Transaction ingestion
    // -------------------------------------------------------------------------

    /**
     * POST /api/v1/settlements/transactions
     *
     * <p>Ingests a single raw expense obligation. The record is validated
     * (positive amount, distinct parties) and persisted. It will be included
     * in the next optimisation pass.</p>
     *
     * <p>Example body:
     * {"amount": 120.50, "description": "Dinner", "payerId": "...", "payeeId": "..."}</p>
     *
     * @param transaction raw expense from the JSON body
     * @return 201 Created with the saved entity (ID and timestamps populated)
     */
    @PostMapping("/transactions")
    public ResponseEntity<ExpenseTransaction> addTransaction(
            @RequestBody ExpenseTransaction transaction) {

        log.info("POST /transactions — payerId={} payeeId={} amount={}",
                transaction.getPayerId(), transaction.getPayeeId(), transaction.getAmount());

        ExpenseTransaction saved = ledgerService.saveTransaction(transaction);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * POST /api/v1/settlements/transactions/batch
     *
     * <p>Ingests a JSON array of raw expenses in one transactional batch.
     * Useful for bulk-importing a trip's receipts. Any single validation
     * failure rolls back the entire batch.</p>
     *
     * @param transactions list of raw expenses from the JSON body
     * @return 201 Created with the list of saved entities
     */
    @PostMapping("/transactions/batch")
    public ResponseEntity<List<ExpenseTransaction>> addTransactions(
            @RequestBody List<ExpenseTransaction> transactions) {

        log.info("POST /transactions/batch — {} records received", transactions.size());

        List<ExpenseTransaction> saved = ledgerService.saveAllTransactions(transactions);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // -------------------------------------------------------------------------
    // Settlement optimisation
    // -------------------------------------------------------------------------

    /**
     * GET /api/v1/settlements/optimize
     *
     * <p>Runs the O(N log N) greedy heap algorithm synchronously over all
     * unsettled transactions. Blocks until the result is ready. Use for
     * small groups or admin tooling where response latency is acceptable.</p>
     *
     * @return 200 OK with the minimal list of synthetic settlement transactions
     */
    @GetMapping("/optimize")
    public ResponseEntity<List<ExpenseTransaction>> optimize() {
        log.info("GET /optimize — running synchronous settlement pass");

        List<ExpenseTransaction> result = ledgerService.optimizeDebts();
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/settlements/optimize/async
     *
     * <p>Triggers the settlement algorithm asynchronously. The I/O thread
     * is released immediately -- Spring MVC resolves the CompletableFuture
     * into a deferred HTTP response once the background thread completes.
     * Response time depends on group size and executor queue depth.</p>
     *
     * @return deferred 200 OK with the settlement list, resolved asynchronously
     */
    @GetMapping("/optimize/async")
    public CompletableFuture<ResponseEntity<List<ExpenseTransaction>>> optimizeAsync() {
        log.info("GET /optimize/async — dispatching to settlement thread pool");

        // thenApply runs on the completing thread (settlement-async-*), not the I/O thread
        return ledgerService.optimizeDebtsAsync()
                .thenApply(ResponseEntity::ok);
    }

    /**
     * POST /api/v1/settlements/optimize/group
     *
     * <p>Async optimisation scoped to a specific set of participants (e.g. a
     * trip or household). Only transactions where both payer and payee appear
     * in the supplied list are included, isolating the result from unrelated
     * ledger activity.</p>
     *
     * <p>Example body: ["uuid-1", "uuid-2", "uuid-3"]</p>
     *
     * @param userIds JSON array of participant UUIDs
     * @return deferred 200 OK with the scoped settlement list
     */
    @PostMapping("/optimize/group")
    public CompletableFuture<ResponseEntity<List<ExpenseTransaction>>> optimizeForGroup(
            @RequestBody List<UUID> userIds) {

        log.info("POST /optimize/group — {} participants", userIds.size());

        return ledgerService.optimizeDebtsForGroup(userIds)
                .thenApply(ResponseEntity::ok);
    }
}
