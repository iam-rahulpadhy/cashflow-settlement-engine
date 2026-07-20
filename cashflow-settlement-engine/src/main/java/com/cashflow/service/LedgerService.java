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

/**
 * Core application service that orchestrates the debt-settlement lifecycle.
 *
 * <p>Sits between the REST layer (SettlementController) and two infrastructure
 * layers: TransactionRepository (PostgreSQL via JPA) and SettlementAlgorithm
 * (pluggable optimisation strategy).</p>
 *
 * <h2>Transactional Boundaries</h2>
 * <p>Write operations use @Transactional so that raw transaction persistence
 * and settlement-flag updates either both commit or both roll back -- ledger
 * consistency must be all-or-nothing.</p>
 *
 * <h2>Async Design</h2>
 * <p>The optimisation pass can be slow for large groups (O(M + N log N)).
 * Methods annotated with @Async run on the "settlement-async-" thread pool
 * configured in application.yml, freeing the HTTP I/O thread immediately.
 * Spring's proxy model guarantees that the @Transactional boundary is opened
 * correctly inside that async thread -- not leaked from the caller's thread.</p>
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

    // -------------------------------------------------------------------------
    // Ingestion
    // -------------------------------------------------------------------------

    /**
     * Validates and persists a single raw expense transaction.
     *
     * @param transaction the raw transaction to save; must not be null
     * @return the saved entity with its generated UUID populated
     * @throws IllegalArgumentException if the transaction fails domain validation
     */
    @Transactional
    public ExpenseTransaction saveTransaction(ExpenseTransaction transaction) {
        validate(transaction);
        log.info("Persisting transaction: {} -> {} for {}",
                transaction.getPayerId(), transaction.getPayeeId(), transaction.getAmount());
        return transactionRepository.save(transaction);
    }

    /**
     * Validates and persists a batch of raw transactions in one DB round-trip.
     *
     * <p>Any validation failure aborts the entire batch -- we don't want a
     * half-committed set of obligations in the ledger.</p>
     *
     * @param transactions list of raw transactions; must not be null or empty
     * @return list of saved entities with generated IDs
     */
    @Transactional
    public List<ExpenseTransaction> saveAllTransactions(List<ExpenseTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            throw new IllegalArgumentException("transaction list must not be null or empty");
        }
        // validate the whole batch before touching the DB
        transactions.forEach(this::validate);
        log.info("Persisting batch of {} transactions", transactions.size());
        return transactionRepository.saveAll(transactions);
    }

    // -------------------------------------------------------------------------
    // Synchronous optimisation
    // -------------------------------------------------------------------------

    /**
     * Loads all unsettled transactions, runs the O(N log N) greedy heap
     * algorithm synchronously, then commits the results atomically.
     *
     * <p>Suitable for small groups or admin/test endpoints. For production
     * workloads with many participants, prefer optimizeDebtsAsync().</p>
     *
     * @return the minimal list of synthetic settlement transactions
     */
    @Transactional
    public List<ExpenseTransaction> optimizeDebts() {
        List<ExpenseTransaction> raw = transactionRepository.findBySettledFalse();
        log.info("Running synchronous optimisation over {} raw transactions", raw.size());

        List<ExpenseTransaction> settlements = settlementAlgorithm.optimizeDebts(raw);

        persistSettlementResult(raw, settlements);
        return settlements;
    }

    // -------------------------------------------------------------------------
    // Asynchronous optimisation
    // -------------------------------------------------------------------------

    /**
     * Runs the full-ledger settlement optimisation asynchronously.
     *
     * <p>Spring dispatches this method to the "settlement-async-" thread pool
     * (see application.yml). The HTTP thread is released immediately and gets
     * back a CompletableFuture it can chain or return as a deferred response.</p>
     *
     * <p>@Async and @Transactional are both proxy-driven. Spring applies the
     * @Async proxy first (submits to thread pool), then the @Transactional proxy
     * opens a new transaction on that background thread -- the two don't
     * interfere because they operate on different proxy layers.</p>
     *
     * @return CompletableFuture resolving to the minimal settlement list
     */
    @Async
    @Transactional
    public CompletableFuture<List<ExpenseTransaction>> optimizeDebtsAsync() {
        List<ExpenseTransaction> raw = transactionRepository.findBySettledFalse();
        // Log the thread name so we can confirm in Phase 4 it's NOT the main thread
        log.info("Async optimisation running on thread: [{}], {} raw transactions",
                Thread.currentThread().getName(), raw.size());

        List<ExpenseTransaction> settlements = settlementAlgorithm.optimizeDebts(raw);

        persistSettlementResult(raw, settlements);
        return CompletableFuture.completedFuture(settlements);
    }

    /**
     * Runs the settlement optimisation scoped to a specific group of users,
     * asynchronously. Only transactions where both payer and payee are in
     * {@code userIds} are included -- isolating a trip or household from the
     * rest of the ledger.
     *
     * @param userIds the participant UUIDs to scope the optimisation to
     * @return CompletableFuture resolving to the scoped settlement list
     */
    @Async
    @Transactional
    public CompletableFuture<List<ExpenseTransaction>> optimizeDebtsForGroup(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("userIds must not be null or empty");
        }
        List<ExpenseTransaction> raw =
                transactionRepository.findUnsettledWithinGroup(userIds, userIds);
        log.info("Async group optimisation on thread [{}]: {} users, {} raw transactions",
                Thread.currentThread().getName(), userIds.size(), raw.size());

        List<ExpenseTransaction> settlements = settlementAlgorithm.optimizeDebts(raw);

        persistSettlementResult(raw, settlements);
        return CompletableFuture.completedFuture(settlements);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Domain validation for a single transaction.
     *
     * <p>We check the three invariants that must hold before any transaction
     * enters the ledger: non-null, positive amount, and distinct parties.
     * Catching these early avoids silent data corruption in the heap algorithm.</p>
     */
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

    /**
     * Atomically marks raw transactions as settled and persists the synthetic
     * settlement output.
     *
     * <p>We mark synthetic settlement transactions as settled=true immediately
     * because they represent the computed ANSWER, not new pending obligations.
     * This ensures findBySettledFalse() never picks them up in a future run
     * and double-counts them in the debt graph.</p>
     *
     * @param raw         the original unsettled transactions to flag
     * @param settlements the synthetic optimised transactions to persist
     */
    private void persistSettlementResult(List<ExpenseTransaction> raw,
                                         List<ExpenseTransaction> settlements) {
        // flag raw transactions so they never re-enter the algorithm
        raw.forEach(tx -> tx.setSettled(true));
        transactionRepository.saveAll(raw);

        // mark synthetic ones settled=true so they serve as an audit log,
        // not as new debts that would pollute the next optimisation run
        settlements.forEach(tx -> tx.setSettled(true));
        transactionRepository.saveAll(settlements);

        log.info("Settlement committed: {} raw marked settled, {} synthetic transactions persisted",
                raw.size(), settlements.size());
    }
}
