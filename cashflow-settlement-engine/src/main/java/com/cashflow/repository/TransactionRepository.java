package com.cashflow.repository;

import com.cashflow.entity.ExpenseTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ExpenseTransaction} persistence.
 *
 * <p>Extends {@link JpaRepository} to inherit the full suite of CRUD and
 * pagination operations.  Custom query methods below are declared as stubs;
 * Spring Data will generate their implementations at boot time via its
 * proxy-based query derivation mechanism.</p>
 *
 * <h2>Query naming convention</h2>
 * <ul>
 *   <li>Methods prefixed {@code findBy…} use Spring Data's method-name DSL.</li>
 *   <li>Methods prefixed {@code query…} use explicit {@code @Query} JPQL for
 *       joins or aggregations that the DSL cannot express cleanly.</li>
 * </ul>
 */
@Repository
public interface TransactionRepository extends JpaRepository<ExpenseTransaction, UUID> {

    // -------------------------------------------------------------------------
    // Derived query methods (Spring Data generates the implementation)
    // -------------------------------------------------------------------------

    /**
     * Returns all unsettled transactions where the given user is the payer.
     *
     * @param payerId the UUID of the debtor user
     * @return list of matching transactions; never {@code null}
     */
    List<ExpenseTransaction> findByPayerIdAndSettledFalse(UUID payerId);

    /**
     * Returns all unsettled transactions where the given user is the payee.
     *
     * @param payeeId the UUID of the creditor user
     * @return list of matching transactions; never {@code null}
     */
    List<ExpenseTransaction> findByPayeeIdAndSettledFalse(UUID payeeId);

    /**
     * Returns every transaction (settled or not) that involves the given user
     * as either payer or payee.  Useful for generating per-user ledger views.
     *
     * @param userId the user UUID to filter on
     * @return combined list of transactions as payer and payee
     */
    @Query("""
        SELECT t FROM ExpenseTransaction t
        WHERE t.payerId = :userId
           OR t.payeeId = :userId
        ORDER BY t.createdAt DESC
        """)
    List<ExpenseTransaction> findAllTransactionsInvolvingUser(@Param("userId") UUID userId);

    /**
     * Returns all raw (unsettled) transactions within a specific settlement group.
     * <p>
     * The engine calls this method to load the full input graph before running
     * the greedy heap optimisation pass.
     * </p>
     *
     * @return list of all unsettled expense transactions
     */
    List<ExpenseTransaction> findBySettledFalse();

    /**
     * Returns all transactions where both parties are in the supplied set of
     * user IDs.  Used to scope the optimisation to a defined group (e.g. a
     * trip or a household) rather than the entire ledger.
     *
     * @param payerIds  set of potential payer UUIDs
     * @param payeeIds  set of potential payee UUIDs (typically same set)
     * @return filtered unsettled transactions
     */
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
