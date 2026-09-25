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
package io.micronaut.web.router.processor;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.web.router.spi.RoutePlanSupport;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A template lowered into the operations of a generated parser: the segments of the matched
 * path, each a literal or a variable with the rule that accepts a segment, and the names of the
 * captured variables. Or the reason a template cannot be lowered.
 *
 * <p>The root template has no segments; it matches the empty path and {@code /}.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class LoweredTemplate {

    private final List<Segment> segments;
    private final List<String> captures;
    private final @Nullable String unsupportedReason;

    private LoweredTemplate(List<Segment> segments, List<String> captures, @Nullable String unsupportedReason) {
        this.segments = segments;
        this.captures = captures;
        this.unsupportedReason = unsupportedReason;
    }

    /**
     * A lowered template.
     *
     * @param segments The segments, in the order of the path
     * @param captures The names of the variables of the variable segments, in the same order
     * @return The lowered template
     * @throws IllegalArgumentException if the captures do not name the variable segments
     */
    public static LoweredTemplate of(List<Segment> segments, List<String> captures) {
        long variables = segments.stream().filter(Segment::isVariable).count();
        if (variables != captures.size()) {
            throw new IllegalArgumentException("The template has " + variables + " variable segments and " + captures.size() + " captures");
        }
        return new LoweredTemplate(List.copyOf(segments), List.copyOf(captures), null);
    }

    /**
     * A template that is not lowered, and is matched at runtime by its engine.
     *
     * @param reason Why
     * @return The result
     */
    public static LoweredTemplate unsupported(String reason) {
        return new LoweredTemplate(List.of(), List.of(), Objects.requireNonNull(reason, "reason"));
    }

    /**
     * @return Whether the template is lowered
     */
    public boolean isSupported() {
        return unsupportedReason == null;
    }

    /**
     * @return The segments, empty for the root template and for a template that is not lowered
     */
    public List<Segment> segments() {
        return segments;
    }

    /**
     * @return The names of the captured variables
     */
    public List<String> captures() {
        return captures;
    }

    /**
     * @return Why the template is not lowered, or {@code null}
     */
    public @Nullable String unsupportedReason() {
        return unsupportedReason;
    }

    @Override
    public String toString() {
        return isSupported() ? segments.toString() : "unsupported: " + unsupportedReason;
    }

    /**
     * A segment of a lowered template: a literal segment, compared exactly, or a variable segment,
     * accepted by a rule.
     *
     * @param literal The literal, or {@code null} for a variable
     * @param rule    The rule of the variable, or {@code null} for a literal
     */
    public record Segment(@Nullable String literal, @Nullable VariableRule rule) {

        /**
         * @param literal The literal, or {@code null} for a variable
         * @param rule    The rule of the variable, or {@code null} for a literal
         */
        public Segment {
            if ((literal == null) == (rule == null)) {
                throw new IllegalArgumentException("A segment is a literal or a variable");
            }
            if (literal != null && (literal.isEmpty() || literal.indexOf('/') >= 0)) {
                throw new IllegalArgumentException("A literal segment is not empty and has no slash: " + literal);
            }
        }

        /**
         * @param literal The text of the segment, without slashes
         * @return A literal segment
         */
        public static Segment literal(String literal) {
            return new Segment(literal, null);
        }

        /**
         * @param rule The rule that accepts a segment
         * @return A variable segment
         */
        public static Segment variable(VariableRule rule) {
            return new Segment(null, rule);
        }

        /**
         * @return Whether the segment is a variable
         */
        public boolean isVariable() {
            return rule != null;
        }

        @Override
        public String toString() {
            return rule != null ? "{" + rule.methodName() + "}" : String.valueOf(literal);
        }
    }

    /**
     * The rule of a variable segment: a public static method {@code boolean method(String path,
     * int slash, int end)} of a runtime class, which the generated parser calls with the normalised
     * path, the index of the slash that starts the segment and the end of the segment.
     *
     * @param ownerType  The name of the class with the method
     * @param methodName The name of the method
     */
    public record VariableRule(String ownerType, String methodName) {

        /**
         * A whole-segment variable of a Micronaut template, see {@link RoutePlanSupport#micronautVariable(String, int, int)}.
         */
        public static final VariableRule MICRONAUT = new VariableRule(RoutePlanSupport.class.getName(), "micronautVariable");

        /**
         * A variable that accepts any segment that is not empty, see {@link RoutePlanSupport#nonEmptySegment(String, int, int)}.
         */
        public static final VariableRule NON_EMPTY_SEGMENT = new VariableRule(RoutePlanSupport.class.getName(), "nonEmptySegment");
    }
}
