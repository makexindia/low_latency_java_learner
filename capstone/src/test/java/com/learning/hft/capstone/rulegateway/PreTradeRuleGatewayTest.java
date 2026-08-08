package com.learning.hft.capstone.rulegateway;

import com.learning.hft.capstone.riskgateway.MmapJournal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;

class PreTradeRuleGatewayTest {

    private static final long SIZE = 1 << 20;

    private PreTradeRuleGateway gateway(MmapJournal journal) {
        // price band [90_000,110_000], maxQty 10_000, maxNotional 1e9
        return new PreTradeRuleGateway(90_000, 110_000, 10_000, 1_000_000_000L, journal);
    }

    @Test
    void acceptsGoodOrderAndJournalsIt(@TempDir Path dir) throws IOException {
        try (MmapJournal j = new MmapJournal(dir.resolve("g.journal"), SIZE)) {
            PreTradeRuleGateway gw = gateway(j);
            assertEquals(-1, gw.check(1, 7, 100_000, 100, (byte) 0)); // accepted
            assertEquals(1, j.recordCount());                          // journaled
        }
    }

    @Test
    void rejectsFatFingerPriceAndRestrictedSymbol(@TempDir Path dir) throws IOException {
        try (MmapJournal j = new MmapJournal(dir.resolve("g.journal"), SIZE)) {
            PreTradeRuleGateway gw = gateway(j);
            assertEquals(PreTradeRuleGateway.FAT_FINGER_PRICE, gw.check(1, 7, 500_000, 100, (byte) 0));
            gw.restrict(42);
            assertEquals(PreTradeRuleGateway.RESTRICTED_SYMBOL, gw.check(2, 42, 100_000, 100, (byte) 0));
            assertEquals(0, j.recordCount()); // nothing accepted -> nothing journaled
        }
    }

    @Test
    void killSwitchBlocksEverythingLive(@TempDir Path dir) throws IOException {
        try (MmapJournal j = new MmapJournal(dir.resolve("g.journal"), SIZE)) {
            PreTradeRuleGateway gw = gateway(j);
            gw.setKillSwitch(true);
            assertEquals(PreTradeRuleGateway.KILL_SWITCH, gw.check(1, 7, 100_000, 100, (byte) 0));
            gw.setKillSwitch(false);
            assertEquals(-1, gw.check(2, 7, 100_000, 100, (byte) 0)); // trading resumes
        }
    }

    @Test
    void disablingARuleFlipsTheDecisionLive(@TempDir Path dir) throws IOException {
        try (MmapJournal j = new MmapJournal(dir.resolve("g.journal"), SIZE)) {
            PreTradeRuleGateway gw = gateway(j);
            // price in band, notional 990M < cap, qty 11_000 > cap -> only MAX_QUANTITY fails
            assertEquals(PreTradeRuleGateway.MAX_QUANTITY, gw.check(1, 7, 90_000, 11_000, (byte) 0));
            gw.disableRule(PreTradeRuleGateway.MAX_QUANTITY);
            assertEquals(-1, gw.check(2, 7, 90_000, 11_000, (byte) 0)); // now accepted
        }
    }
}
