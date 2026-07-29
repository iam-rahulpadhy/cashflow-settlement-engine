package com.cashflow.runner;

import com.cashflow.engine.SettlementAlgorithm;
import com.cashflow.entity.ExpenseTransaction;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/*
 * Proof harness — active only under the "h2test" profile.
 * 6 participants, 11 raw edges -> must collapse to <= 5 (V-1) settlements.
 * Run: ./mvnw spring-boot:run -Dspring-boot.run.profiles=h2test
 */
@Component
@Profile("h2test")
public class TestRunner implements CommandLineRunner {

    private final SettlementAlgorithm settlementAlgorithm;

    private static final UUID ALICE   = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID BOB     = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID CHARLIE = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID DIANA   = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID EVE     = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
    private static final UUID FRANK   = UUID.fromString("ffffffff-0000-0000-0000-000000000006");

    private static final Map<UUID, String> NAMES = Map.of(
            ALICE, "Alice  ", BOB,     "Bob    ",
            CHARLIE, "Charlie", DIANA, "Diana  ",
            EVE,   "Eve    ", FRANK,   "Frank  "
    );

    public TestRunner(SettlementAlgorithm settlementAlgorithm) {
        this.settlementAlgorithm = settlementAlgorithm;
    }

    @Override
    public void run(String... args) {
        List<ExpenseTransaction> raw         = buildDebtGraph();
        List<ExpenseTransaction> settlements = settlementAlgorithm.optimizeDebts(raw);

        printResults(raw, settlements);

        if (settlements.size() > NAMES.size() - 1) {
            throw new IllegalStateException("ASSERTION FAILED: got " + settlements.size()
                    + " transactions, expected <= " + (NAMES.size() - 1));
        }
    }

    private List<ExpenseTransaction> buildDebtGraph() {
        List<ExpenseTransaction> txns = new ArrayList<>();

        txns.add(tx(30, "Dinner share",    BOB,     ALICE));
        txns.add(tx(30, "Dinner share",    CHARLIE, ALICE));
        txns.add(tx(40, "Dinner share",    DIANA,   ALICE));

        txns.add(tx(20, "Grocery share",   CHARLIE, BOB));
        txns.add(tx(25, "Grocery share",   EVE,     BOB));

        txns.add(tx(15, "Drinks share",    DIANA,   CHARLIE));
        txns.add(tx(15, "Drinks share",    FRANK,   CHARLIE));

        txns.add(tx(25, "Transport share", EVE,     DIANA));
        txns.add(tx(25, "Transport share", FRANK,   DIANA));

        txns.add(tx(40, "Lunch share",     FRANK,   EVE));
        txns.add(tx(10, "Lunch share",     ALICE,   EVE));

        return txns;
    }

    private static ExpenseTransaction tx(int amount, String desc, UUID payer, UUID payee) {
        return new ExpenseTransaction(BigDecimal.valueOf(amount), desc, payer, payee);
    }

    private void printResults(List<ExpenseTransaction> raw, List<ExpenseTransaction> settlements) {
        int    v    = NAMES.size();
        String line = "=".repeat(60);
        String thin = "-".repeat(60);

        System.out.println();
        System.out.println(line);
        System.out.println("  CASH FLOW SETTLEMENT ENGINE  --  Algorithm Proof");
        System.out.println(line);
        System.out.printf("  Participants (V)      : %d%n", v);
        System.out.printf("  Raw expense edges (M) : %d%n", raw.size());
        System.out.printf("  Settlement edges      : %d%n", settlements.size());
        System.out.printf("  Maximum allowed (V-1) : %d%n", v - 1);
        System.out.println(thin);
        for (int i = 0; i < settlements.size(); i++) {
            ExpenseTransaction s = settlements.get(i);
            System.out.printf("  [%d] %s -> %s  $%.2f%n",
                    i + 1, NAMES.get(s.getPayerId()), NAMES.get(s.getPayeeId()), s.getAmount());
        }
        System.out.println(thin);
        boolean pass = settlements.size() <= v - 1;
        System.out.printf("  %d raw -> %d settled   Proof: %d <= %d (V-1)   %s%n",
                raw.size(), settlements.size(), settlements.size(), v - 1, pass ? "PASS" : "FAIL");
        System.out.println(line);
        System.out.println();
    }
}
