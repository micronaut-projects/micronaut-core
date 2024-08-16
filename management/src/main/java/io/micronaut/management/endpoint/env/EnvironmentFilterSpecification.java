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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.SupplierUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
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

    private static final Supplier<List<Pattern>> LEGACY_MASK_PATTERNS = SupplierUtil.memoized(() ->
        Stream.of(".*password.*", ".*credential.*", ".*certificate.*", ".*key.*", ".*secret.*", ".*token.*")
            .map(s -> Pattern.compile(s, Pattern.CASE_INSENSITIVE))
            .collect(Collectors.toList())
    );

    private boolean allMasked;
    private final List<Predicate<String>> exclusions = new ArrayList<>();

    EnvironmentFilterSpecification() {
        this.allMasked = true;
    }

    /**
     * Turn on global masking. Items can be unmasked by executing any of the exclude methods.
     *
     * @return this
     */
    public @NonNull EnvironmentFilterSpecification maskAll() {
        allMasked = true;
        return this;
    }

    /**
     * Turn off global masking. Items can be masked by executing any of the exclude methods.
     *
     * @return this
     */
    public @NonNull EnvironmentFilterSpecification maskNone() {
        allMasked = false;
        return this;
    }

    /**
     * Adds a predicate to test property keys. If the {@link #maskAll()} flag is set
     * then this will be used for exceptions (values in clear-text). If the {@link #maskNone()}
     * flag is set then this will be those values that are masked.
     *
     * @param keyPredicate A predicate to match against property keys for masking
     * @return this
     */
    public @NonNull EnvironmentFilterSpecification exclude(@NonNull Predicate<String> keyPredicate) {
        exclusions.add(keyPredicate);
        return this;
    }

    /**
     * Adds literal strings to the list of excluded keys.  If the {@link EnvironmentFilterSpecification#maskAll()} flag is set
     * then this list will be used for exceptions (values in clear-text).  If the {@link EnvironmentFilterSpecification#maskNone()}
     * flag is set then this list will be those values that are masked.
     *
     * @param keys Literal keys that should be excluded
     * @return this
     */
    public @NonNull EnvironmentFilterSpecification exclude(@NonNull String... keys) {
        if (keys.length > 0) {
            if (keys.length == 1) {
                exclusions.add(name -> keys[0].equals(name));
            } else {
                List<String> keysList = Arrays.asList(keys);
                exclusions.add(keysList::contains);
            }
        }
        return this;
    }

    /**
     * Adds regular expression patterns to the list of known masks.  If the {@link #maskAll()} flag is set
     * then this list will be used for exceptions (values in clear-text).  If the {@link #maskNone()}
     * flag is set then this list will be those values that are masked. Patterns must match
     * the entire property key.
     *
     * @param keyPatterns The patterns used to compare keys to exclude them
     * @return this
     */
    public @NonNull EnvironmentFilterSpecification exclude(@NonNull Pattern... keyPatterns) {
        if (keyPatterns.length > 0) {
            if (keyPatterns.length == 1) {
                exclusions.add(name -> keyPatterns[0].matcher(name).matches());
            } else {
                exclusions.add(name -> Arrays.stream(keyPatterns).anyMatch(pattern -> pattern.matcher(name).matches()));
            }
        }
        return this;
    }

    /**
     * Configures the key masking to behave as it did prior to 3.3.0.
     *
     * @return this
     */
    public @NonNull EnvironmentFilterSpecification legacyMasking() {
        allMasked = false;
        for (Pattern pattern : LEGACY_MASK_PATTERNS.get()) {
            exclude(pattern);
        }
        return this;
    }

    FilterResult test(String key) {
        for (Predicate<String> exclusion : exclusions) {
            if (exclusion.test(key)) {
                return allMasked ? FilterResult.PLAIN : FilterResult.MASK;
            }
        }
        return allMasked ? FilterResult.MASK : FilterResult.PLAIN;
    }

    enum FilterResult {
        HIDE,
        MASK,
        PLAIN
    }
}
