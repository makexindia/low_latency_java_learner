package com.learning.hft.decisioning.frameworks.esper;

import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.compiler.client.CompilerArguments;
import com.espertech.esper.compiler.client.EPCompilerProvider;
import com.espertech.esper.runtime.client.EPDeployment;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPRuntimeProvider;

/**
 * Chapter 8 (framework demo) — Esper CEP for streaming rules, <b>off the hot path</b>.
 *
 * <p>Esper compiles an EPL query to JVM bytecode (Esper 8+), giving a declarative way to express
 * windowed/pattern rules over an event stream — ideal for surveillance, alerting, and deriving
 * signals (e.g. a price-spike that drives a skew), where the ruleset is complex and changes more
 * often than the nanosecond matching path. It is NOT what you put on the order-matching hot path
 * (that's the hand-rolled bitset engine); it's the complement for streaming analytics.
 *
 * <p>The query below detects a &gt;0.5% price jump versus the previous tick using a length window and
 * {@code prev()}:
 *
 * <pre>
 *   mvn -q -Pframeworks -pl phase8-decisioning compile exec:java \
 *       -Dexec.mainClass=com.learning.hft.decisioning.frameworks.esper.EsperCepDemo
 * </pre>
 */
public final class EsperCepDemo {

    public static void main(String[] args) {
        Configuration config = new Configuration();
        config.getCommon().addEventType(PriceEvent.class);

        String epl = "@name('spike') "
                + "select symbol, price, prev(1, price) as previous "
                + "from PriceEvent#length(2) "
                + "where price > prev(1, price) * 1.005";

        EPCompiled compiled;
        try {
            compiled = EPCompilerProvider.getCompiler().compile(epl, new CompilerArguments(config));
        } catch (Exception e) {
            throw new RuntimeException("EPL compile failed", e);
        }

        EPRuntime runtime = EPRuntimeProvider.getDefaultRuntime(config);
        try {
            EPDeployment deployment = runtime.getDeploymentService().deploy(compiled);
            runtime.getDeploymentService()
                    .getStatement(deployment.getDeploymentId(), "spike")
                    .addListener((newData, oldData, stmt, rt) -> {
                        for (var event : newData) {
                            System.out.printf("  SPIKE: %s %.3f (prev %.3f)%n",
                                    event.get("symbol"), event.get("price"), event.get("previous"));
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException("deploy failed", e);
        }

        double[] prices = {100.0, 100.2, 101.0, 100.9, 102.5, 102.4};
        System.out.println("feeding EURUSD ticks; Esper flags >0.5% jumps vs previous:");
        for (double p : prices) {
            System.out.printf("tick %.3f%n", p);
            runtime.getEventService().sendEventBean(new PriceEvent("EURUSD", p), "PriceEvent");
        }
        runtime.destroy();
    }

    private EsperCepDemo() {
    }
}
