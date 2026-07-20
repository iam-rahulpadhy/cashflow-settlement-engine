package com.cashflow.engine;

import com.cashflow.entity.ExpenseTransaction;
import com.cashflow.entity.UserNetBalance;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/*
 * Greedy min/max-heap settlement algorithm. Reduces an M-edge debt graph to
 * at most N-1 edges in O(M + N log N) time.
 *
 * Step 1 - balance aggregation [O(M)]:
 *   For each raw transaction (payer P, payee Q, amount A):
 *     netBalance[P] -= A   (P owes more)
 *     netBalance[Q] += A   (Q is owed more)
 *   Anyone at zero is already square and gets dropped.
 *
 * Step 2 - heap split [O(N log N)]:
 *   debtorHeap   = min-heap by netBalance -> head is the biggest debtor
 *   creditorHeap = max-heap by netBalance -> head is the biggest creditor
 *
 * Step 3 - greedy cancellation [O(N log N)]:
 *   Each iteration pairs the biggest debtor with the biggest creditor and
 *   settles min(|debt|, credit) in one payment, zeroing at least one of them.
 *   Loop runs at most N-1 times, each heap op is O(log N).
 *
 * Thread safety: both heaps are LOCAL variables inside optimizeDebts(), never
 * instance fields. This bean is a singleton -- instance-level heaps would be
 * a data race across concurrent async calls.
 */
@Component
public class GreedyHeapSettlementStrategy implements SettlementAlgorithm {

    @Override
    public List<ExpenseTransaction> optimizeDebts(List<ExpenseTransaction> rawTransactions) {
        if (rawTransactions == null || rawTransactions.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, BigDecimal> balances = aggregateBalances(rawTransactions);

        // Local heaps -- one per call, never shared across threads
        PriorityQueue<UserNetBalance> debtorHeap   = new PriorityQueue<>();
        PriorityQueue<UserNetBalance> creditorHeap = new PriorityQueue<>(Comparator.reverseOrder());
        initHeaps(balances, debtorHeap, creditorHeap);

        List<ExpenseTransaction> results = new ArrayList<>();

        while (!debtorHeap.isEmpty() && !creditorHeap.isEmpty()) {
            UserNetBalance debtor   = debtorHeap.poll();
            UserNetBalance creditor = creditorHeap.poll();

            BigDecimal settlement = debtor.getNetBalance().negate().min(creditor.getNetBalance());

            results.add(new ExpenseTransaction(
                    settlement, "Settlement", debtor.getUserId(), creditor.getUserId()));

            debtor.adjustBalance(settlement);
            creditor.adjustBalance(settlement.negate());

            if (debtor.getNetBalance().compareTo(BigDecimal.ZERO) < 0)   debtorHeap.offer(debtor);
            if (creditor.getNetBalance().compareTo(BigDecimal.ZERO) > 0) creditorHeap.offer(creditor);
        }

        return Collections.unmodifiableList(results);
    }

    // Single pass over raw transactions to compute each user's net position.
    private Map<UUID, BigDecimal> aggregateBalances(List<ExpenseTransaction> transactions) {
        Map<UUID, BigDecimal> balances = new HashMap<>();
        for (ExpenseTransaction tx : transactions) {
            balances.merge(tx.getPayerId(), tx.getAmount().negate(), BigDecimal::add);
            balances.merge(tx.getPayeeId(), tx.getAmount(),          BigDecimal::add);
        }
        return balances;
    }

    // Zero-balance users are skipped -- they're already square.
    private void initHeaps(Map<UUID, BigDecimal> balances,
                           PriorityQueue<UserNetBalance> debtorHeap,
                           PriorityQueue<UserNetBalance> creditorHeap) {
        for (Map.Entry<UUID, BigDecimal> e : balances.entrySet()) {
            int cmp = e.getValue().compareTo(BigDecimal.ZERO);
            if      (cmp < 0) debtorHeap.offer(new UserNetBalance(e.getKey(), e.getValue()));
            else if (cmp > 0) creditorHeap.offer(new UserNetBalance(e.getKey(), e.getValue()));
        }
    }
}
