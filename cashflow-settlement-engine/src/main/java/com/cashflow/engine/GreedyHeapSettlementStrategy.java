package com.cashflow.engine;

import com.cashflow.entity.ExpenseTransaction;
import com.cashflow.entity.UserNetBalance;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/*
 * Greedy min/max-heap algorithm — reduces M debt edges to at most N-1 in O(N log N).
 * Heaps are local variables so this singleton is safe across concurrent async calls.
 */
@Component
public class GreedyHeapSettlementStrategy implements SettlementAlgorithm {

    @Override
    public List<ExpenseTransaction> optimizeDebts(List<ExpenseTransaction> rawTransactions) {
        if (rawTransactions == null || rawTransactions.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, BigDecimal> balances = aggregateBalances(rawTransactions);

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

    private Map<UUID, BigDecimal> aggregateBalances(List<ExpenseTransaction> transactions) {
        Map<UUID, BigDecimal> balances = new HashMap<>();
        for (ExpenseTransaction tx : transactions) {
            balances.merge(tx.getPayerId(), tx.getAmount().negate(), BigDecimal::add);
            balances.merge(tx.getPayeeId(), tx.getAmount(),          BigDecimal::add);
        }
        return balances;
    }

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
