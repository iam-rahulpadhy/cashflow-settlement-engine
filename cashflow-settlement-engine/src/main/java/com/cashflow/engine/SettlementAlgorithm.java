package com.cashflow.engine;

import com.cashflow.entity.ExpenseTransaction;
import java.util.List;

/**
 * Strategy interface for debt-graph optimisation.
 *
 * Implementations take a raw list of expense transactions (which may have
 * cycles and redundant edges) and return a semantically equivalent but
 * minimal set of settlement payments. The only implementation today is
 * {@link GreedyHeapSettlementStrategy} -- O(N log N) greedy min/max-heap.
 */
public interface SettlementAlgorithm {

    /**
     * Reduces a raw debt graph to the minimal set of settlement payments.
     * Must not mutate the input list. Must be thread-safe (stateless bean).
     */
    List<ExpenseTransaction> optimizeDebts(List<ExpenseTransaction> rawTransactions);
}
