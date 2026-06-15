package com.cashflow.entity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Non-persistent POJO that represents a single user's <em>aggregate net
 * position</em> across all raw {@link ExpenseTransaction} records in a given
 * settlement group.
 *
 * <h2>Semantics</h2>
 * <p>
 * {@code netBalance} is computed as:
 * <pre>
 *   netBalance = Σ(amounts owed TO this user) − Σ(amounts owed BY this user)
 * </pre>
 * A <em>positive</em> value means the user is a net creditor (others owe them).
 * A <em>negative</em> value means the user is a net debtor (they owe others).
 * A value of {@link BigDecimal#ZERO} means the user is already square.
 * </p>
 *
 * <h2>Usage in the Engine</h2>
 * <p>
 * The {@code GreedyHeapSettlementStrategy} maps each participant UUID to one
 * {@code UserNetBalance} instance, then uses two {@link java.util.PriorityQueue}
 * heaps — one min-heap (largest debtors) and one max-heap (largest creditors) —
 * to greedily pair and cancel obligations in O(N log N) time.
 * </p>
 *
 * <p>This class is intentionally <em>mutable</em>: the algorithm repeatedly
 * adjusts {@code netBalance} as it cancels edges.</p>
 */
public class UserNetBalance implements Comparable<UserNetBalance> {

    /** The application-level user identifier. */
    private final UUID userId;

    /**
     * Running net balance; modified in-place by the settlement algorithm.
     * Starts as the sum of credits minus sum of debits for this user.
     */
    private BigDecimal netBalance;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a new balance record initialised to the supplied net value.
     *
     * @param userId     the owning user; must not be {@code null}
     * @param netBalance initial aggregate balance; may be negative, zero, or positive
     */
    public UserNetBalance(UUID userId, BigDecimal netBalance) {
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        this.userId     = userId;
        this.netBalance = netBalance != null ? netBalance : BigDecimal.ZERO;
    }

    // -------------------------------------------------------------------------
    // Domain helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if this user has a net credit (is owed money).
     */
    public boolean isCreditor() {
        return netBalance.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Returns {@code true} if this user has a net debit (owes money).
     */
    public boolean isDebtor() {
        return netBalance.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Returns {@code true} if this user's account is fully balanced.
     */
    public boolean isSquare() {
        return netBalance.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Adjusts the running net balance by {@code delta}.
     * The engine calls this method as each synthetic settlement transaction is emitted.
     *
     * @param delta the signed amount to add (positive increases net credit)
     */
    public void adjustBalance(BigDecimal delta) {
        this.netBalance = this.netBalance.add(delta);
    }

    // -------------------------------------------------------------------------
    // Comparable — used by PriorityQueue natural ordering
    // -------------------------------------------------------------------------

    /**
     * Compares by {@code netBalance} ascending so that the largest
     * <em>debtor</em> (most-negative balance) surfaces at the head of a
     * min-heap, while the largest <em>creditor</em> surfaces at the head of
     * a max-heap when the comparator is reversed.
     */
    @Override
    public int compareTo(UserNetBalance other) {
        return this.netBalance.compareTo(other.netBalance);
    }

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------

    public UUID getUserId()                        { return userId; }
    public BigDecimal getNetBalance()              { return netBalance; }
    public void setNetBalance(BigDecimal balance)  { this.netBalance = balance; }

    @Override
    public String toString() {
        return "UserNetBalance{userId=" + userId + ", netBalance=" + netBalance + "}";
    }
}
