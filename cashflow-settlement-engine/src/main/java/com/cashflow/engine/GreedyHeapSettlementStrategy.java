package com.cashflow.engine;

import com.cashflow.entity.ExpenseTransaction;
import com.cashflow.entity.UserNetBalance;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * Greedy min/max-heap implementation of {@link SettlementAlgorithm}.
 *
 * <h2>Algorithm Overview</h2>
 * <p>Given N participants who have recorded M raw expense transactions,
 * this strategy reduces the debt graph to the minimum number of directed
 * payment edges required to clear every obligation while preserving
 * each participant's net monetary position.</p>
 *
 * <h2>Phase 1 - Net Balance Aggregation  [O(M)]</h2>
 * <p>Each raw transaction is traversed once to build a userId to UserNetBalance map.
 * For every edge (payer P, payee Q, amount A):
 * <ul>
 *   <li>netBalance[P] -= A  (P becomes more indebted)</li>
 *   <li>netBalance[Q] += A  (Q becomes more credited)</li>
 * </ul>
 * Participants whose net balance is exactly zero are dropped (already square).</p>
 *
 * <h2>Phase 2 - Heap Initialisation  [O(N log N)]</h2>
 * <p>Remaining participants are split into two heaps:
 * <ul>
 *   <li>debtorHeap  - min-heap by ascending netBalance.
 *       Head = largest debtor (most-negative balance).</li>
 *   <li>creditorHeap - max-heap by descending netBalance.
 *       Head = largest creditor (most-positive balance).</li>
 * </ul>
 *
 * <h2>Phase 3 - Greedy Edge Cancellation  [O(N log N)]</h2>
 * <p>While both heaps are non-empty:
 * <ol>
 *   <li>Poll largest debtor D and largest creditor C.</li>
 *   <li>Compute settlement = min(|D.netBalance|, C.netBalance).</li>
 *   <li>Emit synthetic transaction: D pays C that settlement.</li>
 *   <li>Adjust both balances toward zero.</li>
 *   <li>Re-insert any non-zero participant into the correct heap -- O(log N).</li>
 * </ol>
 * Each iteration zeroes at least one participant; loop runs at most N-1 times.</p>
 *
 * <h2>Complexity</h2>
 * <pre>
 *   Phase                     Time           Space
 *   Net balance aggregation   O(M)           O(N)
 *   Heap construction         O(N log N)     O(N)
 *   Greedy cancellation       O(N log N)     O(N)
 *   Total                     O(M + N log N) O(N)
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This bean is a stateless singleton. Both heap objects are created as
 * LOCAL variables inside {@link #optimizeDebts} on every invocation, so
 * concurrent calls from the async thread pool can never share heap state.
 * Do NOT promote them to instance fields -- that would be a data-race bug.</p>
 *
 * @see SettlementAlgorithm
 * @see UserNetBalance
 */
@Component
public class GreedyHeapSettlementStrategy implements SettlementAlgorithm {

    /**
     * Executes the greedy heap optimisation and returns the minimal list of
     * settlement transactions.
     *
     * <p>Both heaps are declared locally here, not as instance fields.
     * This bean is a singleton -- instance-level heap fields would be shared
     * across concurrent async invocations and corrupt mid-algorithm state.
     * Local variables give each call its own isolated heap.</p>
     *
     * @param rawTransactions unsettled expense transactions; must not be null
     * @return minimal list of synthetic settlement transactions
     */
    @Override
    public List<ExpenseTransaction> optimizeDebts(List<ExpenseTransaction> rawTransactions) {
        if (rawTransactions == null || rawTransactions.isEmpty()) {
            return Collections.emptyList();
        }

        // Phase 1: collapse all raw edges into per-user net balances -- O(M)
        Map<UUID, BigDecimal> balances = aggregateBalances(rawTransactions);

        // Phase 2: split into two LOCAL heaps -- O(N log N)
        // debtorHeap  = min-heap: head is the user with the most negative balance
        // creditorHeap = max-heap: head is the user with the most positive balance
        PriorityQueue<UserNetBalance> debtorHeap   = new PriorityQueue<>();
        PriorityQueue<UserNetBalance> creditorHeap = new PriorityQueue<>(Comparator.reverseOrder());
        initHeaps(balances, debtorHeap, creditorHeap);

        // Phase 3: greedy cancellation -- at most N-1 iterations, each O(log N)
        List<ExpenseTransaction> results = new ArrayList<>();
        while (!debtorHeap.isEmpty() && !creditorHeap.isEmpty()) {
            UserNetBalance debtor   = debtorHeap.poll();
            UserNetBalance creditor = creditorHeap.poll();

            // Settle as much as possible in one shot: min(debt owed, credit due)
            BigDecimal debt       = debtor.getNetBalance().negate();  // flip to positive
            BigDecimal credit     = creditor.getNetBalance();
            BigDecimal settlement = debt.min(credit);

            results.add(emitSettlement(debtor, creditor, settlement));

            debtor.adjustBalance(settlement);              // debtor balance moves toward 0
            creditor.adjustBalance(settlement.negate());   // creditor balance moves toward 0

            // Re-insert only if they still have an outstanding balance
            if (debtor.getNetBalance().compareTo(BigDecimal.ZERO) < 0) {
                debtorHeap.offer(debtor);
            }
            if (creditor.getNetBalance().compareTo(BigDecimal.ZERO) > 0) {
                creditorHeap.offer(creditor);
            }
        }

        return Collections.unmodifiableList(results);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Single-pass O(M) scan over all raw transactions to compute each
     * participant's net monetary position.
     *
     * @param rawTransactions source transactions
     * @return map of userId to signed net balance (positive = creditor, negative = debtor)
     */
    private Map<UUID, BigDecimal> aggregateBalances(List<ExpenseTransaction> rawTransactions) {
        Map<UUID, BigDecimal> balances = new HashMap<>();
        for (ExpenseTransaction tx : rawTransactions) {
            // payer owes money -- their balance decreases
            balances.merge(tx.getPayerId(), tx.getAmount().negate(), BigDecimal::add);
            // payee is owed money -- their balance increases
            balances.merge(tx.getPayeeId(), tx.getAmount(), BigDecimal::add);
        }
        return balances;
    }

    /**
     * Populates the debtor (min) and creditor (max) heaps from the balance map.
     * Zero-balance participants are excluded since they are already square.
     *
     * @param balances     pre-computed net balance per user
     * @param debtorHeap   min-heap to populate with debtors (negative balance)
     * @param creditorHeap max-heap to populate with creditors (positive balance)
     */
    private void initHeaps(Map<UUID, BigDecimal> balances,
                           PriorityQueue<UserNetBalance> debtorHeap,
                           PriorityQueue<UserNetBalance> creditorHeap) {
        for (Map.Entry<UUID, BigDecimal> entry : balances.entrySet()) {
            BigDecimal net = entry.getValue();
            int cmp = net.compareTo(BigDecimal.ZERO);
            if (cmp < 0) {
                debtorHeap.offer(new UserNetBalance(entry.getKey(), net));
            } else if (cmp > 0) {
                creditorHeap.offer(new UserNetBalance(entry.getKey(), net));
            }
            // cmp == 0: already square, skip
        }
    }

    /**
     * Builds a synthetic settlement transaction representing one directed payment.
     *
     * @param debtor   the paying participant
     * @param creditor the receiving participant
     * @param amount   exact settlement amount (always positive)
     * @return a new, unsaved ExpenseTransaction ready for batch persistence
     */
    private ExpenseTransaction emitSettlement(UserNetBalance debtor,
                                              UserNetBalance creditor,
                                              BigDecimal amount) {
        return new ExpenseTransaction(amount, "Settlement", debtor.getUserId(), creditor.getUserId());
    }
}
