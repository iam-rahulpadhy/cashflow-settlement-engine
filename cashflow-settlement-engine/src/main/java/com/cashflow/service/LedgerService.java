package com.cashflow.service;

import com.cashflow.engine.SettlementAlgorithm;
import com.cashflow.entity.ExpenseTransaction;
import com.cashflow.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final TransactionRepository repo;
    private final SettlementAlgorithm   algorithm;

    public LedgerService(TransactionRepository repo, SettlementAlgorithm algorithm) {
        this.repo      = repo;
        this.algorithm = algorithm;
    }

    @Transactional
    public ExpenseTransaction save(ExpenseTransaction tx) {
        validate(tx);
        log.info("Saving transaction: {} -> {} ({})", tx.getPayerId(), tx.getPayeeId(), tx.getAmount());
        return repo.save(tx);
    }

    @Transactional
    public List<ExpenseTransaction> settle() {
        List<ExpenseTransaction> raw = repo.findBySettledFalse();
        log.info("Running settlement on {} transactions", raw.size());
        List<ExpenseTransaction> result = algorithm.optimizeDebts(raw);
        markSettled(raw, result);
        return result;
    }

    // Offloads settlement to the async thread pool — keeps the HTTP thread free during heavy computation
    @Async
    @Transactional
    public CompletableFuture<List<ExpenseTransaction>> settleAsync() {
        List<ExpenseTransaction> raw = repo.findBySettledFalse();
        log.info("[{}] Async settlement on {} transactions", Thread.currentThread().getName(), raw.size());
        List<ExpenseTransaction> result = algorithm.optimizeDebts(raw);
        markSettled(raw, result);
        return CompletableFuture.completedFuture(result);
    }

    private void validate(ExpenseTransaction tx) {
        if (tx.getAmount() == null || tx.getAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Amount must be positive");
        if (tx.getPayerId() == null || tx.getPayeeId() == null)
            throw new IllegalArgumentException("payerId and payeeId are required");
        if (tx.getPayerId().equals(tx.getPayeeId()))
            throw new IllegalArgumentException("Payer and payee cannot be the same");
    }

    private void markSettled(List<ExpenseTransaction> raw, List<ExpenseTransaction> settlements) {
        raw.forEach(tx -> tx.setSettled(true));
        repo.saveAll(raw);
        settlements.forEach(tx -> tx.setSettled(true));
        repo.saveAll(settlements);
    }
}
