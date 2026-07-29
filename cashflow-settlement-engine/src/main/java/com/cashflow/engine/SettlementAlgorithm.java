package com.cashflow.engine;

import com.cashflow.entity.ExpenseTransaction;
import java.util.List;

public interface SettlementAlgorithm {

    // Reduces raw debt graph to minimal settlement payments. Must be stateless / thread-safe.
    List<ExpenseTransaction> optimizeDebts(List<ExpenseTransaction> rawTransactions);
}
