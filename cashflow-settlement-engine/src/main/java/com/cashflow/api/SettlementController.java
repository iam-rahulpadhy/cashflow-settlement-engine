package com.cashflow.api;

import com.cashflow.entity.ExpenseTransaction;
import com.cashflow.service.LedgerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/settlements")
public class SettlementController {

    private final LedgerService ledgerService;

    public SettlementController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping("/transactions")
    public ResponseEntity<ExpenseTransaction> addTransaction(@RequestBody ExpenseTransaction tx) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ledgerService.save(tx));
    }

    @GetMapping("/optimize")
    public ResponseEntity<List<ExpenseTransaction>> optimize() {
        return ResponseEntity.ok(ledgerService.settle());
    }

    @GetMapping("/optimize/async")
    public CompletableFuture<ResponseEntity<List<ExpenseTransaction>>> optimizeAsync() {
        return ledgerService.settleAsync().thenApply(ResponseEntity::ok);
    }
}
