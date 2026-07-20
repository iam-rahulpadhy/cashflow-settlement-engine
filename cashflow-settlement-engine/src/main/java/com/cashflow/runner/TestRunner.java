package com.cashflow.runner;

import com.cashflow.engine.SettlementAlgorithm;
import com.cashflow.entity.ExpenseTransaction;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 4 test harness — proves the O(N log N) graph-reduction algorithm
 * produces at most V-1 settlement transactions for a V-person group.
 *
 * <p>Guarded with @Profile("h2test") so it only runs when the application
 * is started with that profile. It bypasses the DB persistence layer and
 * calls the SettlementAlgorithm directly, proving algorithm correctness
 * without any external tooling (no JUnit, no Postman, no DB required).</p>
 *
 * <h2>Test Scenario: 6-person beach trip</h2>
 * <pre>
 *   V = 6 participants, M = 11 raw expense transactions
 *   Net balances after aggregation:
 *     Alice   +90  (creditor)
 *     Bob     +15  (creditor)
 *     Charlie -20  (debtor)
 *     Diana    -5  (debtor)
 *     Eve       0  (already square — algorithm skips)
 *     Frank   -80  (debtor)
 *   Active participants N = 5 (Eve excluded)
 *   Maximum transactions guaranteed by algorithm: N-1 = 4
 * </pre>
 *
 * <p>Run with: {@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=h2test}</p>
 */
@Component
@Profile("h2test")
public class TestRunner implements CommandLineRunner {

    private final SettlementAlgorithm settlementAlgorithm;

    // Fixed UUIDs so output is reproducible across runs
    private static final UUID ALICE   = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID BOB     = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID CHARLIE = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID DIANA   = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID EVE     = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
    private static final UUID FRANK   = UUID.fromString("ffffffff-0000-0000-0000-000000000006");

    // Name lookup for readable console output
    private static final Map<UUID, String> NAMES = Map.of(
            ALICE,   "Alice  ",
            BOB,     "Bob    ",
            CHARLIE, "Charlie",
            DIANA,   "Diana  ",
            EVE,     "Eve    ",
            FRANK,   "Frank  "
    );

    public TestRunner(SettlementAlgorithm settlementAlgorithm) {
        this.settlementAlgorithm = settlementAlgorithm;
    }

    @Override
    public void run(String... args) {
        List<ExpenseTransaction> raw = buildDebtGraph();
        List<ExpenseTransaction> settlements = settlementAlgorithm.optimizeDebts(raw);

        printResults(raw, settlements);

        // Hard assertion so a CI run would fail fast if the algorithm regresses
        int v        = NAMES.size();
        boolean pass = settlements.size() <= v - 1;
        if (!pass) {
            throw new IllegalStateException(
                "ASSERTION FAILED: got " + settlements.size() + " transactions, expected <= " + (v - 1));
        }
    }

    /**
     * Builds the hardcoded 11-edge debt graph for 6 participants.
     *
     * <p>Each ExpenseTransaction(amount, description, payerId, payeeId)
     * encodes: payerId OWES payeeId the given amount.</p>
     *
     * <p>Designed with cycles and redundant paths so the reduction from
     * 11 raw edges to 4 optimal edges is visually dramatic.</p>
     */
    private List<ExpenseTransaction> buildDebtGraph() {
        List<ExpenseTransaction> txns = new ArrayList<>();

        // Alice covered group dinner — three people owe her
        txns.add(tx(30, "Dinner share", BOB,     ALICE));
        txns.add(tx(30, "Dinner share", CHARLIE, ALICE));
        txns.add(tx(40, "Dinner share", DIANA,   ALICE));

        // Bob covered group groceries — two people owe him
        txns.add(tx(20, "Grocery share", CHARLIE, BOB));
        txns.add(tx(25, "Grocery share", EVE,     BOB));

        // Charlie covered group drinks — two people owe him
        txns.add(tx(15, "Drinks share",  DIANA,   CHARLIE));
        txns.add(tx(15, "Drinks share",  FRANK,   CHARLIE));

        // Diana covered group transport — two people owe her
        txns.add(tx(25, "Transport share", EVE,   DIANA));
        txns.add(tx(25, "Transport share", FRANK, DIANA));

        // Eve covered group lunch — two people owe her
        txns.add(tx(40, "Lunch share", FRANK, EVE));
        txns.add(tx(10, "Lunch share", ALICE, EVE));

        return txns;
    }

    private static ExpenseTransaction tx(int amount, String desc, UUID payer, UUID payee) {
        return new ExpenseTransaction(BigDecimal.valueOf(amount), desc, payer, payee);
    }

    private void printResults(List<ExpenseTransaction> raw,
                              List<ExpenseTransaction> settlements) {
        int v     = NAMES.size();
        int vMin1 = v - 1;
        boolean pass = settlements.size() <= vMin1;

        String divider = "=".repeat(62);
        String thin    = "-".repeat(62);

        System.out.println();
        System.out.println(divider);
        System.out.println("   CASH FLOW SETTLEMENT ENGINE  —  Phase 4 Algorithm Proof");
        System.out.println(divider);

        System.out.println();
        System.out.println("  Scenario : 6-person beach trip (dinner, groceries, drinks,");
        System.out.println("             transport, lunch — all split unevenly)");
        System.out.println();
        System.out.printf("  Participants            (V)   : %d%n", v);
        System.out.printf("  Raw expense edges       (M)   : %d%n", raw.size());
        System.out.printf("  Settlement transactions        : %d%n", settlements.size());
        System.out.printf("  Theoretical maximum     (V-1) : %d%n", vMin1);
        System.out.println();
        System.out.println(thin);
        System.out.println("  Net balance aggregation (who owes / is owed overall):");
        System.out.println(thin);
        System.out.printf("    Alice    +$90.00  (creditor)%n");
        System.out.printf("    Bob      +$15.00  (creditor)%n");
        System.out.printf("    Charlie  -$20.00  (debtor)%n");
        System.out.printf("    Diana    - $5.00  (debtor)%n");
        System.out.printf("    Eve       $0.00   (already square — skipped)%n");
        System.out.printf("    Frank    -$80.00  (debtor)%n");
        System.out.println();
        System.out.println(thin);
        System.out.println("  Optimal settlement plan (greedy max/min-heap result):");
        System.out.println(thin);
        for (int i = 0; i < settlements.size(); i++) {
            ExpenseTransaction s = settlements.get(i);
            System.out.printf("    [%d]  %s  ->  %s  $%.2f%n",
                    i + 1,
                    NAMES.get(s.getPayerId()),
                    NAMES.get(s.getPayeeId()),
                    s.getAmount());
        }
        System.out.println();
        System.out.println(thin);
        System.out.printf("  Graph reduction  :  %d raw edges  ->  %d settlement edges%n",
                raw.size(), settlements.size());
        System.out.printf("  Proof  :  %d <= %d (V-1)   %s%n",
                settlements.size(), vMin1, pass ? "✓  PASS" : "✗  FAIL");
        System.out.println(divider);
        System.out.println();
    }
}
