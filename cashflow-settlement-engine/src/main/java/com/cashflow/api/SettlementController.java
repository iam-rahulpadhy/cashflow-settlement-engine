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

/*
 * REST API for the settlement engine, versioned under /api/v1/settlements.
 *
 * Sync endpoints hold the Tomcat thread. Async endpoints return a
 * CompletableFuture; Spring MVC unwraps it into a deferred HTTP response so
 * the I/O thread is released as soon as the future is handed back.
 */
@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    private static final Logger log = LoggerFactory.getLogger(SettlementController.class);

    private final LedgerService ledgerService;

    public SettlementController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    // POST /transactions -- ingest a single raw expense obligation
    @PostMapping("/transactions")
    public ResponseEntity<ExpenseTransaction> addTransaction(
            @RequestBody ExpenseTransaction transaction) {

        log.info("POST /transactions payer={} payee={} amount={}",
                transaction.getPayerId(), transaction.getPayeeId(), transaction.getAmount());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ledgerService.saveTransaction(transaction));
    }

    // POST /transactions/batch -- bulk ingest; one validation failure rolls back the whole batch
    @PostMapping("/transactions/batch")
    public ResponseEntity<List<ExpenseTransaction>> addTransactions(
            @RequestBody List<ExpenseTransaction> transactions) {

        log.info("POST /transactions/batch -- {} records", transactions.size());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ledgerService.saveAllTransactions(transactions));
    }

    // GET /optimize -- synchronous; fine for small groups or admin tooling
    @GetMapping("/optimize")
    public ResponseEntity<List<ExpenseTransaction>> optimize() {
        log.info("GET /optimize -- synchronous settlement pass");
        return ResponseEntity.ok(ledgerService.optimizeDebts());
    }

    // GET /optimize/async -- non-blocking; I/O thread released immediately
    @GetMapping("/optimize/async")
    public CompletableFuture<ResponseEntity<List<ExpenseTransaction>>> optimizeAsync() {
        log.info("GET /optimize/async -- dispatching to settlement thread pool");
        return ledgerService.optimizeDebtsAsync().thenApply(ResponseEntity::ok);
    }

    // POST /optimize/group -- async, scoped to a participant set (trip, household, etc.)
    @PostMapping("/optimize/group")
    public CompletableFuture<ResponseEntity<List<ExpenseTransaction>>> optimizeForGroup(
            @RequestBody List<UUID> userIds) {

        log.info("POST /optimize/group -- {} participants", userIds.size());
        return ledgerService.optimizeDebtsForGroup(userIds).thenApply(ResponseEntity::ok);
    }
}
