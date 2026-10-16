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

import io.micronaut.inject.utils.JsonWriter;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision.Outcome;
import io.micronaut.python.processing.staticcompile.StaticCompilationDecision.Reason;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * Writes the decisions of a compilation as {@code decisions.jsonl} (one JSON object per line, the
 * first one describing the plan) and {@code summary.txt} (the decisions grouped by outcome with
 * totals per outcome and per reason), and keeps them as {@code decisions.properties}, the state
 * an incremental build reads back to complete its own decisions with those of the sources it did
 * not plan.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public final class StaticCompilationReport {

    /**
     * The name of the machine-readable file.
     */
    public static final String DECISIONS_FILE = "decisions.jsonl";

    /**
     * The name of the human-readable file.
     */
    public static final String SUMMARY_FILE = "summary.txt";

    /**
     * The name of the state file an incremental build reads back.
     */
    public static final String STATE_FILE = "decisions.properties";

    private static final String STATE_VERSION = "1";

    private StaticCompilationReport() {
    }

    /**
     * Writes the report. An incremental build plans the affected sources only: the decisions of the
     * previous report for every other source are kept, so both files stay complete, and the plan
     * record says the build was incremental. A planned source without decisions (its functions
     * were removed) loses its previous ones.
     *
     * @param directory      The directory, created when missing
     * @param mode           The mode of the compilation
     * @param decisions      The decisions of this build
     * @param plannedSources The paths of the sources this build planned, or {@code null} when it planned all of them
     * @return Every decision the report now holds: the retained ones, then this build's
     */
    public static List<StaticCompilationDecision> write(Path directory, StaticCompilationMode mode, List<StaticCompilationDecision> decisions, @Nullable Set<String> plannedSources) {
        try {
            Files.createDirectories(directory);
            List<StaticCompilationDecision> all = new ArrayList<>();
            if (plannedSources != null) {
                for (StaticCompilationDecision retained : readState(directory.resolve(STATE_FILE))) {
                    if (retained.sourcePath() == null || !plannedSources.contains(retained.sourcePath())) {
                        all.add(retained);
                    }
                }
            }
            all.addAll(decisions);
            writeState(directory.resolve(STATE_FILE), all);
            List<String> lines = new ArrayList<>(all.size() + 1);
            lines.add(new JsonWriter().beginObject()
                .name("record").value("plan")
                .name("mode").value(mode.optionValue())
                .name("coverage").value(plannedSources == null ? "full" : "incremental")
                .endObject().toString());
            for (StaticCompilationDecision decision : all) {
                lines.add(decision.toJson());
            }
            Files.write(directory.resolve(DECISIONS_FILE), lines, StandardCharsets.UTF_8);
            Files.writeString(directory.resolve(SUMMARY_FILE), summary(all), StandardCharsets.UTF_8);
            return List.copyOf(all);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write the static compilation report to " + directory, e);
        }
    }

    /**
     * The decisions the state file of a previous build holds: none when there is no file, when a
     * previous version wrote it, or when it cannot be read, since an incomplete report is the worst
     * that follows.
     *
     * @param file The state file
     * @return The decisions
     */
    public static List<StaticCompilationDecision> readState(Path file) {
        if (!Files.exists(file)) {
            return List.of();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            if (!STATE_VERSION.equals(properties.getProperty("version"))) {
                return List.of();
            }
            int count = Integer.parseInt(properties.getProperty("decisions", "0"));
            List<StaticCompilationDecision> decisions = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                StaticCompilationDecision decision = StaticCompilationDecision.fromProperties(properties, "decision." + i + '.');
                if (decision != null) {
                    decisions.add(decision);
                }
            }
            return decisions;
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    private static void writeState(Path file, List<StaticCompilationDecision> decisions) throws IOException {
        Properties properties = new Properties() {
            @Override
            public synchronized Set<Map.Entry<Object, Object>> entrySet() {
                // stored in key order, so the file only changes when the decisions do
                Map<Object, Object> sorted = new TreeMap<>();
                for (Map.Entry<Object, Object> entry : super.entrySet()) {
                    sorted.put(entry.getKey(), entry.getValue());
                }
                return sorted.entrySet();
            }
        };
        properties.setProperty("version", STATE_VERSION);
        properties.setProperty("decisions", Integer.toString(decisions.size()));
        for (int i = 0; i < decisions.size(); i++) {
            decisions.get(i).writeTo(properties, "decision." + i + '.');
        }
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            properties.store(writer, "The decisions of the last static compilation, read back by an incremental build");
        }
    }

    /**
     * The human-readable summary: the decisions grouped by outcome, each with its reasons, then the
     * totals per outcome and per reason rule.
     *
     * @param decisions The decisions
     * @return The summary
     */
    public static String summary(List<StaticCompilationDecision> decisions) {
        StringBuilder out = new StringBuilder();
        Map<Outcome, List<StaticCompilationDecision>> byOutcome = new LinkedHashMap<>();
        for (Outcome outcome : Outcome.ALL) {
            byOutcome.put(outcome, new ArrayList<>());
        }
        for (StaticCompilationDecision decision : decisions) {
            byOutcome.computeIfAbsent(decision.outcome(), outcome -> new ArrayList<>()).add(decision);
        }
        for (Map.Entry<Outcome, List<StaticCompilationDecision>> entry : byOutcome.entrySet()) {
            for (StaticCompilationDecision decision : entry.getValue()) {
                out.append(String.format("%-13s %s", entry.getKey().name(), decision.qualifiedName()));
                if (decision.span() != null) {
                    out.append("  ").append(decision.span().location());
                }
                out.append('\n');
                if (decision.outcome() == Outcome.COMPILED || decision.outcome() == Outcome.CANDIDATE) {
                    out.append(String.format("              statements %d · java calls %d · bridge calls %d · helpers %d%n",
                        decision.stats().statements(), decision.stats().javaCalls(), decision.stats().bridgeCalls(), decision.stats().helperCalls()));
                }
                for (Reason reason : decision.reasons()) {
                    out.append("              [").append(reason.rule()).append("] ");
                    if (reason.span() != null) {
                        out.append(reason.span().location()).append("  ");
                    }
                    out.append(reason.message()).append('\n');
                }
            }
        }
        out.append('\n');
        for (Map.Entry<Outcome, List<StaticCompilationDecision>> entry : byOutcome.entrySet()) {
            out.append(String.format("%-13s %d%n", entry.getKey().name(), entry.getValue().size()));
        }
        Map<String, Integer> byRule = new TreeMap<>();
        for (StaticCompilationDecision decision : decisions) {
            for (Reason reason : decision.reasons()) {
                byRule.merge(reason.rule(), 1, Integer::sum);
            }
        }
        if (!byRule.isEmpty()) {
            out.append('\n');
            byRule.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(e -> out.append(String.format("%-32s %d%n", e.getKey(), e.getValue())));
        }
        return out.toString();
    }
}
