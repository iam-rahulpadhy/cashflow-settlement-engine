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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/*
 * Orchestrates the settlement lifecycle: ingestion, optimisation, persistence.
 *
 * The async methods run on the "settlement-async-" thread pool (application.yml)
 * so large optimisation passes never block the Tomcat I/O thread. Spring opens
 * a fresh @Transactional boundary inside the async thread, so @Async and
 * @Transactional on the same method work correctly -- no proxy collision.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final TransactionRepository transactionRepository;
    private final SettlementAlgorithm   settlementAlgorithm;

    public LedgerService(TransactionRepository transactionRepository,
                         SettlementAlgorithm settlementAlgorithm) {
        this.transactionRepository = transactionRepository;
        this.settlementAlgorithm   = settlementAlgorithm;
    }

    @Transactional
    public ExpenseTransaction saveTransaction(ExpenseTransaction transaction) {
        validate(transaction);
        log.info("Persisting: {} -> {} amount={}",
                transaction.getPayerId(), transaction.getPayeeId(), transaction.getAmount());
        return transactionRepository.save(transaction);
    }

    @Transactional
    public List<ExpenseTransaction> saveAllTransactions(List<ExpenseTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            throw new IllegalArgumentException("transaction list must not be null or empty");
        }
        // Validate the whole batch before touching the DB -- partial commits are messy.
        transactions.forEach(this::validate);
        log.info("Batch persisting {} transactions", transactions.size());
        return transactionRepository.saveAll(transactions);
    }

    @Transactional
    public List<ExpenseTransaction> optimizeDebts() {
        List<ExpenseTransaction> raw = transactionRepository.findBySettledFalse();
        log.info("Sync optimisation: {} raw transactions", raw.size());
        List<ExpenseTransaction> settlements = settlementAlgorithm.optimizeDebts(raw);
        persistSettlementResult(raw, settlements);
        return settlements;
    }

    // @Async dispatches to the thread pool; the controller gets back a
    // CompletableFuture that Spring MVC resolves as a deferred HTTP response.
    @Async
    @Transactional
    public CompletableFuture<List<ExpenseTransaction>> optimizeDebtsAsync() {
        List<ExpenseTransaction> raw = transactionRepository.findBySettledFalse();
        log.info("Async optimisation on [{}]: {} raw transactions",
                Thread.currentThread().getName(), raw.size());
        List<ExpenseTransaction> settlements = settlementAlgorithm.optimizeDebts(raw);
        persistSettlementResult(raw, settlements);
        return CompletableFuture.completedFuture(settlements);
    }

    @Async
    @Transactional
    public CompletableFuture<List<ExpenseTransaction>> optimizeDebtsForGroup(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("userIds must not be null or empty");
        }
        List<ExpenseTransaction> raw =
                transactionRepository.findUnsettledWithinGroup(userIds, userIds);
        log.info("Async group optimisation on [{}]: {} users, {} transactions",
                Thread.currentThread().getName(), userIds.size(), raw.size());
        List<ExpenseTransaction> settlements = settlementAlgorithm.optimizeDebts(raw);
        persistSettlementResult(raw, settlements);
        return CompletableFuture.completedFuture(settlements);
    }

    private void validate(ExpenseTransaction tx) {
        if (tx == null) {
            throw new IllegalArgumentException("transaction must not be null");
        }
        if (tx.getAmount() == null || tx.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive, got: " + tx.getAmount());
        }
        if (tx.getPayerId() == null || tx.getPayeeId() == null) {
            throw new IllegalArgumentException("payerId and payeeId must not be null");
        }
        if (tx.getPayerId().equals(tx.getPayeeId())) {
            throw new IllegalArgumentException("payer and payee cannot be the same user");
        }
    }

    // Raw rows are flagged settled so they never re-enter the algorithm.
    // Synthetic rows are saved as settled=true too -- they are the computed answer,
    // not new pending debts, so they should not pollute the next optimisation run.
    private void persistSettlementResult(List<ExpenseTransaction> raw,
                                         List<ExpenseTransaction> settlements) {
        raw.forEach(tx -> tx.setSettled(true));
        transactionRepository.saveAll(raw);
        settlements.forEach(tx -> tx.setSettled(true));
        transactionRepository.saveAll(settlements);
        log.info("Committed: {} raw settled, {} synthetic persisted", raw.size(), settlements.size());
    }
}
