package com.learning.hft.capstone.riskgateway;

import java.io.IOException;
import java.nio.file.Path;

/**
 * POC 3 — runnable demo: credit checks + durable mmap journaling, then replay.
 *
 * <pre>
 *   mvn -q -pl capstone exec:java -Dexec.mainClass=com.learning.hft.capstone.riskgateway.RiskGatewayDemo
 * </pre>
 */
public final class RiskGatewayDemo {

    public static void main(String[] args) throws IOException {
        Path file = Path.of(System.getProperty("java.io.tmpdir"), "learning-risk.journal");

        try (MmapJournal journal = new MmapJournal(file, 1 << 20)) {
            RiskGateway gw = new RiskGateway(journal);
            gw.setLimit(1001L, 10_000);

            submit(gw, 1001L, 3_000);   // ok
            submit(gw, 1001L, 4_000);   // ok
            submit(gw, 1001L, 5_000);   // reject (only 3_000 left)
            submit(gw, 2002L, 100);     // reject (unknown client)

            System.out.println("remaining credit for 1001 = " + gw.remaining(1001L));
            System.out.println("journaled orders (replayed from mmap):");
            journal.replay((clientId, notional, seq) ->
                    System.out.printf("  seq=%d client=%d notional=%d%n", seq, clientId, notional));
        }
    }

    private static void submit(RiskGateway gw, long client, long notional) {
        boolean ok = gw.check(client, notional);
        System.out.printf("order client=%d notional=%d -> %s%n",
                client, notional, ok ? "ACCEPTED (journaled)" : "REJECTED");
    }

    private RiskGatewayDemo() {
    }
}
