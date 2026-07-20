package com.cashflow.repository;

import com.cashflow.entity.ExpenseTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<ExpenseTransaction, UUID> {

    List<ExpenseTransaction> findByPayerIdAndSettledFalse(UUID payerId);

    List<ExpenseTransaction> findByPayeeIdAndSettledFalse(UUID payeeId);

    // Primary input for a full-ledger optimisation pass.
    List<ExpenseTransaction> findBySettledFalse();

    @Query("""
        SELECT t FROM ExpenseTransaction t
        WHERE t.payerId = :userId
           OR t.payeeId = :userId
        ORDER BY t.createdAt DESC
        """)
    List<ExpenseTransaction> findAllTransactionsInvolvingUser(@Param("userId") UUID userId);

    // Scopes optimisation to a specific group -- trip, household, etc.
    @Query("""
        SELECT t FROM ExpenseTransaction t
        WHERE t.payerId IN :payerIds
          AND t.payeeId IN :payeeIds
          AND t.settled = false
        ORDER BY t.amount DESC
        """)
    List<ExpenseTransaction> findUnsettledWithinGroup(
        @Param("payerIds") List<UUID> payerIds,
        @Param("payeeIds") List<UUID> payeeIds
    );
}
