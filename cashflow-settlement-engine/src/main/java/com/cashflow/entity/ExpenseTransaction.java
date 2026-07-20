package com.cashflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/*
 * A single directed debt: payerId owes payeeId the given amount.
 *
 * Before optimisation the table holds raw, potentially cyclical obligations.
 * After the engine runs, raw rows are flagged settled=true and replaced by
 * the minimal synthetic settlement set (also stored here as settled=true --
 * an audit log so they are never re-fed into the algorithm).
 *
 * UUID primary key instead of a sequence so IDs can be generated client-side
 * without a DB round-trip.
 */
@Entity
@Table(
    name = "expense_transactions",
    indexes = {
        @Index(name = "idx_expense_payer",   columnList = "payer_id"),
        @Index(name = "idx_expense_payee",   columnList = "payee_id"),
        @Index(name = "idx_expense_created", columnList = "created_at")
    }
)
public class ExpenseTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    // Precision 19, scale 4 handles large sums with sub-cent accuracy for pro-rata splits.
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    // TEXT instead of VARCHAR to avoid truncation on long receipt descriptions.
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "payer_id", nullable = false, columnDefinition = "uuid")
    private UUID payerId;

    @Column(name = "payee_id", nullable = false, columnDefinition = "uuid")
    private UUID payeeId;

    // false = pending obligation; true = absorbed by the settlement engine.
    @Column(name = "settled", nullable = false)
    private boolean settled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    protected ExpenseTransaction() {}

    public ExpenseTransaction(BigDecimal amount, String description, UUID payerId, UUID payeeId) {
        this.amount      = amount;
        this.description = description;
        this.payerId     = payerId;
        this.payeeId     = payeeId;
    }

    public UUID       getId()                  { return id; }
    public BigDecimal getAmount()              { return amount; }
    public void       setAmount(BigDecimal a)  { this.amount = a; }
    public String     getDescription()         { return description; }
    public void       setDescription(String d) { this.description = d; }
    public UUID       getPayerId()             { return payerId; }
    public void       setPayerId(UUID id)      { this.payerId = id; }
    public UUID       getPayeeId()             { return payeeId; }
    public void       setPayeeId(UUID id)      { this.payeeId = id; }
    public boolean    isSettled()              { return settled; }
    public void       setSettled(boolean s)    { this.settled = s; }
    public Instant    getCreatedAt()           { return createdAt; }
    public Instant    getUpdatedAt()           { return updatedAt; }

    @Override
    public String toString() {
        return "ExpenseTransaction{id=" + id + ", payer=" + payerId
                + ", payee=" + payeeId + ", amount=" + amount + ", settled=" + settled + "}";
    }
}
