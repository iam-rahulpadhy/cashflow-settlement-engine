package com.cashflow.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single, directed monetary obligation between two users.
 * <p>
 * A row in this table encodes: "The payer owes the payee {@code amount} units
 * of currency for the stated {@code description}."  Before settlement
 * optimisation these may be redundant or cyclical; the engine's job is to
 * reduce them to the minimal equivalent set.
 * </p>
 *
 * <p><b>Table design note:</b> UUIDs are used as primary keys rather than
 * auto-increment longs so that records can be generated client-side or in
 * distributed edge services without a database round-trip.</p>
 */
@Entity
@Table(
    name = "expense_transactions",
    indexes = {
        @Index(name = "idx_expense_payer",  columnList = "payer_id"),
        @Index(name = "idx_expense_payee",  columnList = "payee_id"),
        @Index(name = "idx_expense_created", columnList = "created_at")
    }
)
public class ExpenseTransaction {

    /** Globally unique identifier for this transaction record. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    /**
     * The monetary value of the obligation.
     * <p>
     * Stored with precision 19 and scale 4 to safely represent large sums
     * while retaining sub-cent accuracy required for pro-rata splits.
     * Must be strictly positive; negative values are illegal at the domain
     * level and rejected by the service layer.
     * </p>
     */
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * Human-readable label for the expense (e.g. "Dinner at Nobu", "Flight SFO-JFK").
     * Stored as TEXT to avoid arbitrary VARCHAR truncation on long receipt descriptions.
     */
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /**
     * The user who initiated (and owes) this payment.
     * Foreign-key semantics are enforced at the application layer rather than
     * via a DB constraint so that the settlement engine can operate on
     * external/imported transaction sets.
     */
    @Column(name = "payer_id", nullable = false, columnDefinition = "uuid")
    private UUID payerId;

    /**
     * The user who is owed the money in this obligation.
     */
    @Column(name = "payee_id", nullable = false, columnDefinition = "uuid")
    private UUID payeeId;

    /**
     * Whether this transaction has been absorbed into a settled / optimised set.
     * Raw unsettled rows have {@code false}; after the engine runs they are
     * logically superseded and flagged {@code true}.
     */
    @Column(name = "settled", nullable = false)
    private boolean settled = false;

    /** Wall-clock timestamp of when the transaction was first persisted. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Last modification timestamp, updated whenever settlement state changes. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // -------------------------------------------------------------------------
    // Lifecycle callbacks
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Required by JPA; prefer the builder-style factory for application code. */
    protected ExpenseTransaction() {}

    /**
     * Convenience constructor for the service layer.
     *
     * @param amount      positive monetary value
     * @param description free-text label
     * @param payerId     UUID of the owing party
     * @param payeeId     UUID of the owed party
     */
    public ExpenseTransaction(BigDecimal amount, String description, UUID payerId, UUID payeeId) {
        this.amount      = amount;
        this.description = description;
        this.payerId     = payerId;
        this.payeeId     = payeeId;
    }

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------

    public UUID getId()                    { return id; }

    public BigDecimal getAmount()          { return amount; }
    public void setAmount(BigDecimal a)    { this.amount = a; }

    public String getDescription()         { return description; }
    public void setDescription(String d)   { this.description = d; }

    public UUID getPayerId()               { return payerId; }
    public void setPayerId(UUID id)        { this.payerId = id; }

    public UUID getPayeeId()               { return payeeId; }
    public void setPayeeId(UUID id)        { this.payeeId = id; }

    public boolean isSettled()             { return settled; }
    public void setSettled(boolean s)      { this.settled = s; }

    public Instant getCreatedAt()          { return createdAt; }
    public Instant getUpdatedAt()          { return updatedAt; }

    @Override
    public String toString() {
        return "ExpenseTransaction{id=" + id
            + ", payerId=" + payerId
            + ", payeeId=" + payeeId
            + ", amount=" + amount
            + ", settled=" + settled + "}";
    }
}
