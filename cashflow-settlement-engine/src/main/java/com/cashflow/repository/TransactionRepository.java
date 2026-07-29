package com.cashflow.repository;

import com.cashflow.entity.ExpenseTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<ExpenseTransaction, UUID> {

    List<ExpenseTransaction> findBySettledFalse();
}
