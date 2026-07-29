package com.cashflow.entity;

import java.math.BigDecimal;
import java.util.UUID;

// positive netBalance = creditor, negative = debtor, zero = already square
public class UserNetBalance implements Comparable<UserNetBalance> {

    private final UUID userId;
    private BigDecimal netBalance;

    public UserNetBalance(UUID userId, BigDecimal netBalance) {
        if (userId == null) throw new IllegalArgumentException("userId must not be null");
        this.userId     = userId;
        this.netBalance = netBalance != null ? netBalance : BigDecimal.ZERO;
    }

    public void adjustBalance(BigDecimal delta) {
        this.netBalance = this.netBalance.add(delta);
    }

    @Override
    public int compareTo(UserNetBalance other) {
        return this.netBalance.compareTo(other.netBalance);
    }

    public UUID       getUserId()                 { return userId; }
    public BigDecimal getNetBalance()             { return netBalance; }
    public void       setNetBalance(BigDecimal b) { this.netBalance = b; }

    @Override
    public String toString() {
        return "UserNetBalance{userId=" + userId + ", netBalance=" + netBalance + "}";
    }
}
