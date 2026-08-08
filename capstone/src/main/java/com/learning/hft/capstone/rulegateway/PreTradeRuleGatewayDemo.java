package com.learning.hft.capstone.rulegateway;

import com.learning.hft.capstone.riskgateway.MmapJournal;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Capstone POC 4 — runnable demo: per-request rule evaluation with a live kill switch, plus a
 * scheduled streaming quote skew.
 *
 * <pre>
 *   mvn -q -pl capstone compile exec:java \
 *       -Dexec.mainClass=com.learning.hft.capstone.rulegateway.PreTradeRuleGatewayDemo
 * </pre>
 */
public final class PreTradeRuleGatewayDemo {

    public static void main(String[] args) throws IOException {
        Path file = Path.of(System.getProperty("java.io.tmpdir"), "learning-rulegw.journal");

        try (MmapJournal journal = new MmapJournal(file, 1 << 20)) {
            PreTradeRuleGateway gw =
                    new PreTradeRuleGateway(90_000, 110_000, 10_000, 1_000_000_000L, journal);
            gw.restrict(666); // a restricted symbol

            System.out.println("-- pre-trade rule checks --");
            submit(gw, 1, 7, 100_000, 100);     // accepted
            submit(gw, 2, 7, 500_000, 100);     // fat-finger price
            submit(gw, 3, 7, 100_000, 50_000);  // over quantity
            submit(gw, 4, 666, 100_000, 100);   // restricted symbol

            System.out.println("operator engages KILL SWITCH (live)...");
            gw.setKillSwitch(true);
            submit(gw, 5, 7, 100_000, 100);     // now blocked
            gw.setKillSwitch(false);
            System.out.println("kill switch off; journaled so far = " + journal.recordCount());
        }

        System.out.println();
        System.out.println("-- scheduled streaming quote skew (activates at t=100) --");
        QuoteSkewEngine skew = new QuoteSkewEngine(0, 100, 0.001);
        skew.onQuote(100.0, 102.0);
        skew.advanceTime(50);
        System.out.printf("t=50  active=%b quotes=%.4f/%.4f%n",
                skew.isSkewActive(), skew.bidQuote(), skew.askQuote());
        skew.advanceTime(150);
        System.out.printf("t=150 active=%b quotes=%.4f/%.4f%n",
                skew.isSkewActive(), skew.bidQuote(), skew.askQuote());
    }

    private static void submit(PreTradeRuleGateway gw, long id, int symbol, long price, long qty) {
        int failed = gw.check(id, symbol, price, qty, (byte) 0);
        System.out.printf("order %d (sym=%d px=%d qty=%d) -> %s%n",
                id, symbol, price, qty, failed < 0 ? "ACCEPTED" : "REJECTED by rule " + failed);
    }

    private PreTradeRuleGatewayDemo() {
    }
}
