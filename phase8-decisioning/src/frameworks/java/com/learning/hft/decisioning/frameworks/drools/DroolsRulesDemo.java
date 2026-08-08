package com.learning.hft.decisioning.frameworks.drools;

import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.utils.KieHelper;

/**
 * Chapter 8 (framework demo) — Drools (RETE) for pre-trade business rules, <b>off the hot path</b>.
 *
 * <p>Drools compiles rules into a RETE network that incrementally matches facts against patterns —
 * powerful for large, frequently-changing business/compliance rulesets maintained by analysts. But
 * it allocates and reflects, so it belongs in the risk/compliance tier, NOT the nanosecond matching
 * path (that's the hand-rolled bitset engine). Knowing this trade-off is the senior point.
 *
 * <p>The ruleset is embedded as DRL text and compiled in-memory via {@link KieHelper}:
 *
 * <pre>
 *   mvn -q -Pframeworks -pl phase8-decisioning compile exec:java \
 *       -Dexec.mainClass=com.learning.hft.decisioning.frameworks.drools.DroolsRulesDemo
 * </pre>
 */
public final class DroolsRulesDemo {

    private static final String DRL = """
            package com.learning.hft.decisioning.frameworks.drools;
            import com.learning.hft.decisioning.frameworks.drools.TradeOrder;

            rule "Max quantity"
            when
                $o : TradeOrder( quantity > 10000 )
            then
                $o.reject("MAX_QUANTITY");
            end

            rule "Max notional"
            when
                $o : TradeOrder( notional > 1000000000 )
            then
                $o.reject("MAX_NOTIONAL");
            end
            """;

    public static void main(String[] args) {
        KieBase kieBase = new KieHelper()
                .addContent(DRL, ResourceType.DRL)
                .build();

        submit(kieBase, new TradeOrder(1, 100, 10_000_000L));      // ok
        submit(kieBase, new TradeOrder(1, 20_000, 20_000_000L));   // fails max quantity
        submit(kieBase, new TradeOrder(1, 100, 5_000_000_000L));   // fails max notional
    }

    private static void submit(KieBase kieBase, TradeOrder order) {
        KieSession session = kieBase.newKieSession();
        try {
            session.insert(order);
            session.fireAllRules();
        } finally {
            session.dispose();
        }
        String verdict = order.getRejectedBy() == null
                ? "ACCEPTED" : "REJECTED by " + order.getRejectedBy();
        System.out.printf("qty=%d notional=%d -> %s%n",
                order.getQuantity(), order.getNotional(), verdict);
    }

    private DroolsRulesDemo() {
    }
}
