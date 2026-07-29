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

@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    private static final Logger log = LoggerFactory.getLogger(SettlementController.class);

    private final LedgerService ledgerService;

    public SettlementController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/transactions")
    public ResponseEntity<ExpenseTransaction> addTransaction(@RequestBody ExpenseTransaction transaction) {
        log.info("POST /transactions payer={} payee={} amount={}",
                transaction.getPayerId(), transaction.getPayeeId(), transaction.getAmount());
        return ResponseEntity.status(HttpStatus.CREATED).body(ledgerService.saveTransaction(transaction));
    }

    @PostMapping("/transactions/batch")
    public ResponseEntity<List<ExpenseTransaction>> addTransactions(@RequestBody List<ExpenseTransaction> transactions) {
        log.info("POST /transactions/batch -- {} records", transactions.size());
        return ResponseEntity.status(HttpStatus.CREATED).body(ledgerService.saveAllTransactions(transactions));
    }

    @GetMapping("/optimize")
    public ResponseEntity<List<ExpenseTransaction>> optimize() {
        log.info("GET /optimize");
        return ResponseEntity.ok(ledgerService.optimizeDebts());
    }

    @GetMapping("/optimize/async")
    public CompletableFuture<ResponseEntity<List<ExpenseTransaction>>> optimizeAsync() {
        log.info("GET /optimize/async");
        return ledgerService.optimizeDebtsAsync().thenApply(ResponseEntity::ok);
    }

    @PostMapping("/optimize/group")
    public CompletableFuture<ResponseEntity<List<ExpenseTransaction>>> optimizeForGroup(@RequestBody List<UUID> userIds) {
        log.info("POST /optimize/group -- {} participants", userIds.size());
        return ledgerService.optimizeDebtsForGroup(userIds).thenApply(ResponseEntity::ok);
    }
}
