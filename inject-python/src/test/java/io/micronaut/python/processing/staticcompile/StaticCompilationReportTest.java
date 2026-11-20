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
        String json = decision.toJson();
        assertTrue(json.startsWith("{\"record\":\"decision\",\"name\":\"shop.Pricing.total\",\"source\":\"src/shop/pricing.py\",\"line\":14,\"column\":5,\"outcome\":\"SKIPPED\",\"scope\":\"MODE\","
            + "\"reasons\":[{\"rule\":\"unsupported-statement\",\"message\":\"a \\\"with\\\" statement\\nhas no static lowering\",\"location\":\"src/shop/pricing.py:16:9\",\"hint\":\"Rewrite with the compiled statements: "), json);
        assertTrue(json.endsWith("\"}],\"stats\":{\"statements\":3,\"javaCalls\":0,\"bridgeCalls\":0,\"helperCalls\":0}}"), json);
    }

    @Test
    void theDelegationOfACompiledBodyIsReported(@TempDir Path directory) throws IOException {
        StaticCompilationDecision retried = new StaticCompilationDecision("shop.Pricing.total", new SourceSpan("src/shop/pricing.py", 14, 5, 14, 9),
            Outcome.COMPILED, Scope.MODE, List.of(), new Stats(3, 1, 0, 0), "retry");
        StaticCompilationDecision kept = new StaticCompilationDecision("shop.Pricing.tax", new SourceSpan("src/shop/pricing.py", 20, 5, 20, 9),
            Outcome.COMPILED, Scope.MODE, List.of(), new Stats(1, 0, 0, 0), "none(parameter [rates] is a [java.util.Map], which a Python argument does not reach unchanged)");

        Properties properties = new Properties();
        kept.writeTo(properties, "decision.0.");
        assertEquals(kept, StaticCompilationDecision.fromProperties(properties, "decision.0."));
        assertTrue(retried.toJson().endsWith("\"helperCalls\":0},\"delegation\":\"retry\"}"), retried.toJson());
        assertNull(decision("shop.Pricing.total", "src/shop/pricing.py", 14, Outcome.SKIPPED).delegation());

        StaticCompilationReport.write(directory, StaticCompilationMode.ALL, List.of(retried, kept), null);
        String summary = Files.readString(directory.resolve(StaticCompilationReport.SUMMARY_FILE));
        assertTrue(summary.contains("COMPILED      shop.Pricing.total  src/shop/pricing.py:14:5\n"
            + "              statements 3 · java calls 1 · bridge calls 0 · helpers 0\n"
            + "              python callers: retry\n"), summary);
        assertTrue(summary.contains("              python callers: none(parameter [rates] is a [java.util.Map], which a Python argument does not reach unchanged)\n"), summary);
        assertTrue(summary.contains("delegation none         1\ndelegation retry        1\n"), summary);
    }

    @Test
    void everyReasonOfTheSummaryCarriesItsFix(@TempDir Path directory) throws IOException {
        StaticCompilationReport.write(directory, StaticCompilationMode.ALL, List.of(
            decision("shop.Orders.place", "src/shop/orders.py", 8, Outcome.SKIPPED,
                new Reason("unhinted-parameter", "parameter [order] has no type hint", SourceSpan.at("src/shop/orders.py", 8, 22)),
                new Reason("unsupported-statement", "a With statement has no static lowering", SourceSpan.at("src/shop/orders.py", 10, 9)))
        ), null);

        String summary = Files.readString(directory.resolve(StaticCompilationReport.SUMMARY_FILE));
        assertTrue(summary.contains("              [unhinted-parameter] src/shop/orders.py:8:22  parameter [order] has no type hint\n"
            + "              fix: Hint the parameter with a Java type"), summary);
        assertTrue(summary.contains("              [unsupported-statement] src/shop/orders.py:10:9  a With statement has no static lowering\n"
            + "              fix: Rewrite with the compiled statements"), summary);
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

    @Test
    void anIncrementalBuildDropsTheFunctionsRemovedFromAPlannedSource(@TempDir Path directory) {
        StaticCompilationReport.write(directory, StaticCompilationMode.ALL, List.of(
            decision("shop.Orders.place", "src/shop/orders.py", 8, Outcome.CANDIDATE),
            decision("shop.Orders.cancel", "src/shop/orders.py", 20, Outcome.CANDIDATE),
            decision("shop.Pricing.total", "src/shop/pricing.py", 14, Outcome.CANDIDATE)
        ), null);

        // cancel was removed from orders.py, the only source this build planned
        List<StaticCompilationDecision> all = StaticCompilationReport.write(directory, StaticCompilationMode.ALL, List.of(
            decision("shop.Orders.place", "src/shop/orders.py", 8, Outcome.SKIPPED, new Reason("unhinted-return", "no return hint", null))
        ), Set.of("src/shop/orders.py"));

        assertEquals(List.of("shop.Pricing.total", "shop.Orders.place"), all.stream().map(StaticCompilationDecision::qualifiedName).toList());
        assertEquals(Outcome.SKIPPED, all.get(1).outcome());
        assertEquals(all, StaticCompilationReport.readState(directory.resolve(StaticCompilationReport.STATE_FILE)));
    }

    @Test
    void plannedSourcesMatchTheRetainedDecisionsWhateverThePathForm(@TempDir Path directory) throws IOException {
        Path sources = Files.createDirectories(directory.resolve("real/src"));
        Path orders = Files.writeString(sources.resolve("orders.py"), "");
        Path pricing = Files.writeString(sources.resolve("pricing.py"), "");
        Path legacy = Files.writeString(sources.resolve("legacy.py"), "");
        Path link = directory.resolve("link");
        boolean linked;
        try {
            Files.createSymbolicLink(link, directory.resolve("real"));
            linked = true;
        } catch (IOException | UnsupportedOperationException e) {
            linked = false;
        }
        // the decisions record the sources as the parser saw them: through the link, or relative and not normalized
        String ordersAsParsed = linked ? link.resolve("src/orders.py").toString() : sources.resolve("../src/orders.py").toString();
        String legacyAsParsed = linked ? link.resolve("src/legacy.py").toString() : sources.resolve("./legacy.py").toString();
        Path report = directory.resolve("report");
        StaticCompilationReport.write(report, StaticCompilationMode.ALL, List.of(
            decision("Orders.place", ordersAsParsed, 8, Outcome.CANDIDATE),
            decision("Pricing.total", pricing.toString(), 14, Outcome.CANDIDATE),
            decision("Legacy.run", legacyAsParsed, 3, Outcome.CANDIDATE)
        ), null);
        Files.delete(legacy);

        // an incremental build names the affected sources by their real paths, a deleted one included
        List<StaticCompilationDecision> all = StaticCompilationReport.write(report, StaticCompilationMode.ALL, List.of(
            decision("Orders.place", ordersAsParsed, 8, Outcome.SKIPPED)
        ), Set.of(orders.toRealPath().toString(), sources.toRealPath().resolve("legacy.py").toString()));

        assertEquals(List.of("Pricing.total", "Orders.place"), all.stream().map(StaticCompilationDecision::qualifiedName).toList());
        assertEquals(StaticCompilationReport.sourceKey(orders.toString()), StaticCompilationReport.sourceKey(ordersAsParsed));
    }

    @Test
    void aFullWriteSharingTheDirectoryKeepsTheOtherSourcesAndSaysFull(@TempDir Path directory) throws IOException {
        StaticCompilationReport.write(directory, StaticCompilationMode.ALL, List.of(
            decision("first.Service.run", "src/first/service.py", 4, Outcome.CANDIDATE)
        ), null);
        List<StaticCompilationDecision> all = StaticCompilationReport.write(directory, StaticCompilationMode.ALL, List.of(
            decision("second.Service.run", "src/second/service.py", 4, Outcome.CANDIDATE)
        ), Set.of("src/second/service.py"), false);

        assertEquals(List.of("first.Service.run", "second.Service.run"), all.stream().map(StaticCompilationDecision::qualifiedName).toList());
        assertEquals("{\"record\":\"plan\",\"mode\":\"all\",\"coverage\":\"full\"}", Files.readAllLines(directory.resolve(StaticCompilationReport.DECISIONS_FILE)).get(0));
    }
}
