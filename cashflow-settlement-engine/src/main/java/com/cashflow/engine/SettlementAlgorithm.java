package com.cashflow.engine;

import com.cashflow.entity.ExpenseTransaction;
import java.util.List;

/**
 * Strategy interface for debt-settlement optimisation algorithms.
 *
 * <p>Implementations receive a raw list of {@link ExpenseTransaction} records
 * (which may contain cycles, redundant edges, and N-to-N obligations) and
 * must return a <em>semantically equivalent</em> but minimised list of
 * synthetic settlement transactions that satisfies every original obligation
 * using the fewest possible payment legs.</p>
 *
 * <h2>Contract</h2>
 * <ol>
 *   <li><b>Conservation:</b> The net balance of every participant must be
 *       identical before and after optimisation. No money is created or lost.</li>
 *   <li><b>Minimality:</b> The returned list should contain no more
 *       transactions than strictly necessary to clear all debts.</li>
 *   <li><b>Immutability of input:</b> Implementations must not mutate the
 *       supplied {@code rawTransactions} list.</li>
 *   <li><b>Thread safety:</b> Implementations are expected to be stateless
 *       so a single bean instance can serve concurrent requests safely.</li>
 * </ol>
 *
 * <h2>Known implementations</h2>
 * <ul>
 *   <li>{@link GreedyHeapSettlementStrategy} — O(N log N) greedy
 *       min/max-heap approach; optimal for typical group-expense graphs.</li>
 * </ul>
 */
public interface SettlementAlgorithm {

    /**
     * Optimises a set of raw debt obligations into the minimal equivalent
     * set of settlement payments.
     *
     * @param rawTransactions the unoptimised list of expense transactions;
     *                        must not be {@code null} but may be empty
     * @return a new list of synthetic settlement transactions that
     *         collectively clear every debt; never {@code null}, may be empty
     * @throws IllegalArgumentException if {@code rawTransactions} is {@code null}
     */
    List<ExpenseTransaction> optimizeDebts(List<ExpenseTransaction> rawTransactions);
}
