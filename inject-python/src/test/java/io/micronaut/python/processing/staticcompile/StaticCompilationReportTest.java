/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.processing.staticcompile;

import io.micronaut.python.processing.model.SourceSpan;
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision.Outcome;
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision.Reason;
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision.Scope;
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision.Stats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticCompilationReportTest {

    private static StaticCompilationDecision decision(String name, String source, int line, Outcome outcome, Reason... reasons) {
        return new StaticCompilationDecision(name, new SourceSpan(source, line, 5, line, 9), outcome, Scope.MODE, List.of(reasons), new Stats(3, 0, 0, 0));
    }

    @Test
    void decisionsRoundTripThroughTheState() {
        StaticCompilationDecision decision = decision("shop.Pricing.total", "src/shop/pricing.py", 14, Outcome.SKIPPED,
            new Reason("unsupported-statement", "a \"with\" statement\nhas no static lowering", SourceSpan.at("src/shop/pricing.py", 16, 9)));

        Properties properties = new Properties();
        decision.writeTo(properties, "decision.0.");
        StaticCompilationDecision read = StaticCompilationDecision.fromProperties(properties, "decision.0.");

        assertEquals(decision, read);
        assertEquals("src/shop/pricing.py", read.sourcePath());
        assertEquals("src/shop/pricing.py:16:9", read.reasons().get(0).span().location());
        assertNull(StaticCompilationDecision.fromProperties(properties, "decision.1."));
        assertEquals("{\"record\":\"decision\",\"name\":\"shop.Pricing.total\",\"source\":\"src/shop/pricing.py\",\"line\":14,\"column\":5,\"outcome\":\"SKIPPED\",\"scope\":\"MODE\","
            + "\"reasons\":[{\"rule\":\"unsupported-statement\",\"message\":\"a \\\"with\\\" statement\\nhas no static lowering\",\"location\":\"src/shop/pricing.py:16:9\"}],"
            + "\"stats\":{\"statements\":3,\"javaCalls\":0,\"bridgeCalls\":0,\"helperCalls\":0}}", decision.toJson());
    }

    @Test
    void anIncrementalBuildKeepsTheDecisionsOfTheSourcesItDidNotPlan(@TempDir Path directory) throws IOException {
        StaticCompilationReport.write(directory, StaticCompilationMode.ALL, List.of(
            decision("shop.Pricing.total", "src/shop/pricing.py", 14, Outcome.CANDIDATE),
            decision("shop.Orders.place", "src/shop/orders.py", 8, Outcome.SKIPPED, new Reason("unhinted-return", "no return hint", null)),
            decision("shop.Legacy.run", "src/shop/legacy.py", 3, Outcome.EXCLUDED)
        ), null);

        List<StaticCompilationDecision> all = StaticCompilationReport.write(directory, StaticCompilationMode.ALL, List.of(
            decision("shop.Orders.place", "src/shop/orders.py", 8, Outcome.CANDIDATE)
        ), Set.of("src/shop/orders.py", "src/shop/legacy.py"));

        assertEquals(List.of("shop.Pricing.total", "shop.Orders.place"), all.stream().map(StaticCompilationDecision::qualifiedName).toList());
        List<String> lines = Files.readAllLines(directory.resolve(StaticCompilationReport.DECISIONS_FILE));
        assertEquals("{\"record\":\"plan\",\"mode\":\"all\",\"coverage\":\"incremental\"}", lines.get(0));
        assertEquals(3, lines.size());
        assertEquals(all, StaticCompilationReport.readState(directory.resolve(StaticCompilationReport.STATE_FILE)));
        String summary = Files.readString(directory.resolve(StaticCompilationReport.SUMMARY_FILE));
        assertTrue(summary.contains("CANDIDATE     shop.Pricing.total  src/shop/pricing.py:14:5"), summary);
        assertTrue(summary.contains("CANDIDATE     shop.Orders.place"), summary);
        assertTrue(summary.contains("CANDIDATE     2\n"), summary);
        assertTrue(summary.contains("EXCLUDED      0\n"), summary);
    }
}
