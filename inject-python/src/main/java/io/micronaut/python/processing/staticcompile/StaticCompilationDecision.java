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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.utils.JsonWriter;
import io.micronaut.python.processing.model.SourceSpan;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * The decision taken for one candidate function: whether its body is compiled to Java, how the
 * decision to attempt it was reached, and every reason it was not compiled.
 *
 * @param qualifiedName The function, as {@code Class.method} or the module-level function's name
 * @param span          Where the function is declared, when known
 * @param outcome       The outcome
 * @param scope         The declaration that decided whether compilation was attempted
 * @param reasons       Why the body is not compiled; empty when it is
 * @param stats         What the body contains
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public record StaticCompilationDecision(String qualifiedName,
                                        @Nullable SourceSpan span,
                                        Outcome outcome,
                                        Scope scope,
                                        List<Reason> reasons,
                                        Stats stats) {

    public StaticCompilationDecision {
        Objects.requireNonNull(qualifiedName, "qualifiedName");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(scope, "scope");
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        stats = stats == null ? Stats.NONE : stats;
    }

    /**
     * @return The path of the source the function is declared in, or {@code null} when unknown
     */
    public @Nullable String sourcePath() {
        return span == null ? null : span.path();
    }

    /**
     * @return The decision as one line of JSON, with stable field names
     */
    public String toJson() {
        JsonWriter json = new JsonWriter().beginObject()
            .name("record").value("decision")
            .name("name").value(qualifiedName)
            .name("source").value(sourcePath())
            .name("line").value(span == null ? null : (Number) span.line())
            .name("column").value(span == null ? null : (Number) span.column())
            .name("outcome").value(outcome.name())
            .name("scope").value(scope.name())
            .name("reasons").beginArray();
        for (Reason reason : reasons) {
            json.beginObject()
                .name("rule").value(reason.rule())
                .name("message").value(reason.message())
                .name("location").value(reason.span() == null ? null : reason.span().location())
                .endObject();
        }
        return json.endArray()
            .name("stats").beginObject()
            .name("statements").value(stats.statements())
            .name("javaCalls").value(stats.javaCalls())
            .name("bridgeCalls").value(stats.bridgeCalls())
            .name("helperCalls").value(stats.helperCalls())
            .endObject()
            .endObject()
            .toString();
    }

    /**
     * Stores the decision as properties, the form an incremental build reads it back in.
     *
     * @param properties The properties
     * @param prefix     The prefix of the decision's keys
     */
    public void writeTo(Properties properties, String prefix) {
        properties.setProperty(prefix + "name", qualifiedName);
        writeSpan(properties, prefix, span);
        properties.setProperty(prefix + "outcome", outcome.name());
        properties.setProperty(prefix + "scope", scope.name());
        properties.setProperty(prefix + "statements", Integer.toString(stats.statements()));
        properties.setProperty(prefix + "javaCalls", Integer.toString(stats.javaCalls()));
        properties.setProperty(prefix + "bridgeCalls", Integer.toString(stats.bridgeCalls()));
        properties.setProperty(prefix + "helperCalls", Integer.toString(stats.helperCalls()));
        properties.setProperty(prefix + "reasons", Integer.toString(reasons.size()));
        for (int i = 0; i < reasons.size(); i++) {
            Reason reason = reasons.get(i);
            String reasonPrefix = prefix + "reason." + i + '.';
            properties.setProperty(reasonPrefix + "rule", reason.rule());
            properties.setProperty(reasonPrefix + "message", reason.message());
            writeSpan(properties, reasonPrefix, reason.span());
        }
    }

    private static void writeSpan(Properties properties, String prefix, @Nullable SourceSpan span) {
        if (span != null) {
            properties.setProperty(prefix + "source", span.path());
            properties.setProperty(prefix + "line", Integer.toString(span.line()));
            properties.setProperty(prefix + "column", Integer.toString(span.column()));
            properties.setProperty(prefix + "endLine", Integer.toString(span.endLine()));
            properties.setProperty(prefix + "endColumn", Integer.toString(span.endColumn()));
        }
    }

    private static @Nullable SourceSpan readSpan(Properties properties, String prefix) {
        String source = properties.getProperty(prefix + "source");
        if (source == null) {
            return null;
        }
        return new SourceSpan(source, intOf(properties.getProperty(prefix + "line")), intOf(properties.getProperty(prefix + "column")),
            intOf(properties.getProperty(prefix + "endLine")), intOf(properties.getProperty(prefix + "endColumn")));
    }

    /**
     * Reads a decision back from the properties {@link #writeTo(Properties, String)} stored.
     *
     * @param properties The properties
     * @param prefix     The prefix of the decision's keys
     * @return The decision, or {@code null} when the properties hold none under the prefix
     * @throws IllegalArgumentException When a stored value is not what the decision needs
     */
    public static @Nullable StaticCompilationDecision fromProperties(Properties properties, String prefix) {
        String name = properties.getProperty(prefix + "name");
        if (name == null) {
            return null;
        }
        SourceSpan span = readSpan(properties, prefix);
        List<Reason> reasons = new ArrayList<>();
        int count = intOf(properties.getProperty(prefix + "reasons"));
        for (int i = 0; i < count; i++) {
            String reasonPrefix = prefix + "reason." + i + '.';
            reasons.add(new Reason(
                Objects.requireNonNull(properties.getProperty(reasonPrefix + "rule"), reasonPrefix + "rule"),
                Objects.requireNonNull(properties.getProperty(reasonPrefix + "message"), reasonPrefix + "message"),
                readSpan(properties, reasonPrefix)));
        }
        Stats stats = new Stats(
            intOf(properties.getProperty(prefix + "statements")),
            intOf(properties.getProperty(prefix + "javaCalls")),
            intOf(properties.getProperty(prefix + "bridgeCalls")),
            intOf(properties.getProperty(prefix + "helperCalls")));
        return new StaticCompilationDecision(name, span,
            Outcome.of(Objects.requireNonNull(properties.getProperty(prefix + "outcome"), prefix + "outcome")),
            Scope.of(Objects.requireNonNull(properties.getProperty(prefix + "scope"), prefix + "scope")),
            reasons, stats);
    }

    private static int intOf(@Nullable String value) {
        return value == null ? 0 : Integer.parseInt(value);
    }

    /**
     * What became of a candidate function.
     */
    public enum Outcome {
        /**
         * The body is compiled to Java.
         */
        COMPILED,
        /**
         * The body passes every check the planner makes; a code generator would compile it.
         */
        CANDIDATE,
        /**
         * Compilation was attempted and refused for the listed reasons, which the author can address.
         */
        SKIPPED,
        /**
         * A decorator excluded the function, its class or its module.
         */
        EXCLUDED,
        /**
         * The function can never be compiled: its kind, its class or its signature rule it out.
         */
        NOT_CANDIDATE;

        /**
         * The outcomes, in the order the report lists them.
         */
        public static final List<Outcome> ALL = List.of(COMPILED, CANDIDATE, SKIPPED, EXCLUDED, NOT_CANDIDATE);

        /**
         * @param name The name of an outcome, as {@link #name()} spells it
         * @return The outcome
         * @throws IllegalArgumentException for any other name
         */
        public static Outcome of(String name) {
            for (Outcome outcome : ALL) {
                if (outcome.name().equals(name)) {
                    return outcome;
                }
            }
            throw new IllegalArgumentException("Unknown outcome [" + name + "]");
        }
    }

    /**
     * The declaration that decided whether compilation was attempted: the nearest {@code CompileStatic}
     * switch, or the compilation's mode when there is none.
     */
    public enum Scope {
        /**
         * The mode of the compilation.
         */
        MODE,
        /**
         * A switch on the module.
         */
        MODULE,
        /**
         * A switch on the class.
         */
        CLASS,
        /**
         * A switch on the function itself.
         */
        FUNCTION;

        /**
         * The scopes, nearest declaration last.
         */
        public static final List<Scope> ALL = List.of(MODE, MODULE, CLASS, FUNCTION);

        /**
         * @param name The name of a scope, as {@link #name()} spells it
         * @return The scope
         * @throws IllegalArgumentException for any other name
         */
        public static Scope of(String name) {
            for (Scope scope : ALL) {
                if (scope.name().equals(name)) {
                    return scope;
                }
            }
            throw new IllegalArgumentException("Unknown scope [" + name + "]");
        }
    }

    /**
     * Why a body is not compiled.
     *
     * @param rule    The stable identifier of the reason
     * @param message What was found
     * @param span    Where, when known
     */
    public record Reason(String rule, String message, @Nullable SourceSpan span) {
        public Reason {
            Objects.requireNonNull(rule, "rule");
            Objects.requireNonNull(message, "message");
        }
    }

    /**
     * What a body contains, for the report.
     *
     * @param statements  The statements of the body
     * @param javaCalls   The calls to Java methods and constructors
     * @param bridgeCalls The calls that cross into Python
     * @param helperCalls The calls to the runtime's Python-semantics helpers
     */
    public record Stats(int statements, int javaCalls, int bridgeCalls, int helperCalls) {
        /**
         * No statistics.
         */
        public static final Stats NONE = new Stats(0, 0, 0, 0);
    }
}
