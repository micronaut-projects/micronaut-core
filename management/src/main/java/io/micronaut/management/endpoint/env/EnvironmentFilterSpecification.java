/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.management.endpoint.env;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is passed to an instance of {@link EnvironmentEndpointFilter} (if one is defined) each time the {@link EnvironmentEndpoint}
 * is invoked.
 *
 * @author Tim Yates
 * @since 3.3.0
 */
public final class EnvironmentFilterSpecification {

    private static final List<Pattern> LEGACY_MASK_PATTERNS =
            Stream.of(".*password.*", ".*credential.*", ".*certificate.*", ".*key.*", ".*secret.*", ".*token.*")
            .map(s -> Pattern.compile(s, Pattern.CASE_INSENSITIVE))
            .collect(Collectors.toList());

    private boolean allMasked;
    private final List<Predicate<String>> exclusions = new ArrayList<>();

    EnvironmentFilterSpecification() {
        this.allMasked = true;
    }

    /**
     * Turn on global masking. Items can be unmasked by adding {@link Pattern}s to {@link #exclude(Pattern...)}, literals to {@link #exclude(String...)}, or {@link Predicate}s to {@link #exclude(Predicate)}.
     *
     * @return itself for chaining calls.
     */
    public @NotNull EnvironmentFilterSpecification maskAll() {
        allMasked = true;
        return this;
    }

    /**
     * Turn off global masking. Items can be masked by adding {@link Pattern}s to {@link #exclude(Pattern...)}, literals to {@link #exclude(String...)}, or {@link Predicate}s to {@link #exclude(Predicate)}.
     *
     * @return itself for chaining calls.
     */
    public @NotNull EnvironmentFilterSpecification maskNone() {
        allMasked = false;
        return this;
    }

    /**
     * Adds a predicate to the list of known masks.  If the {@link EnvironmentFilterSpecification#maskAll()} flag is set
     * then this will be used for exceptions (values in clear-text).  If the {@link EnvironmentFilterSpecification#maskNone()}
     * flag is set then this will be those values that are masked.
     *
     * @param propertySourceNamePredicates one or more predicates to match against property names for masking.
     * @return itself for chaining calls.
     */
    public @NotNull EnvironmentFilterSpecification exclude(@NotNull Predicate<String> propertySourceNamePredicates) {
        exclusions.add(propertySourceNamePredicates);
        return this;
    }

    /**
     * Adds literal strings to the list of known masks.  If the {@link EnvironmentFilterSpecification#maskAll()} flag is set
     * then this list will be used for exceptions (values in clear-text).  If the {@link EnvironmentFilterSpecification#maskNone()}
     * flag is set then this list will be those values that are masked.
     *
     * @param propertySourceNamePredicates one or more predicates to match against property names for masking.
     * @return itself for chaining calls.
     */
    public @NotNull EnvironmentFilterSpecification exclude(@NotNull String... propertySourceNamePredicates) {
        exclusions.add(name -> Arrays.asList(propertySourceNamePredicates).contains(name));
        return this;
    }

    /**
     * Adds regular expression patterns to the list of known masks.  If the {@link EnvironmentFilterSpecification#maskAll()} flag is set
     * then this list will be used for exceptions (values in clear-text).  If the {@link EnvironmentFilterSpecification#maskNone()}
     * flag is set then this list will be those values that are masked.
     *
     * @param propertySourceNamePredicates one or more predicates to match against property names for masking.
     * @return itself for chaining calls.
     */
    public @NotNull EnvironmentFilterSpecification exclude(@NotNull Pattern... propertySourceNamePredicates) {
        exclusions.add(name -> Arrays.stream(propertySourceNamePredicates).anyMatch(pattern -> pattern.matcher(name).matches()));
        return this;
    }

    /**
     * Adds the masking rules from legacy pre 3.3.0.  Effectively the same as calling {@link EnvironmentFilterSpecification#maskNone()}
     * and adding pattern predicates via {@link #exclude(Pattern...)} to mask anything containing the words {@code password},
     * {@code credential}, {@code certificate}, {@code key}, {@code secret} or {@code token}.
     *
     * @return itself for chaining calls.
     */
    public @NotNull EnvironmentFilterSpecification legacyMasking() {
        allMasked = false;
        for (Pattern pattern : LEGACY_MASK_PATTERNS) {
            exclude(pattern);
        }
        return this;
    }

    FilterResult test(String propertySourceName) {
        if (exclusions.stream().anyMatch(p -> p.test(propertySourceName))) {
            return allMasked ? FilterResult.PLAIN : FilterResult.MASK;
        }
        return allMasked ? FilterResult.MASK : FilterResult.PLAIN;
    }

    enum FilterResult {
        HIDE,
        MASK,
        PLAIN
    }
}
