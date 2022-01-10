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

import io.micronaut.core.annotation.Nullable;

import javax.validation.constraints.NotNull;
import java.security.Principal;
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

    private static final List<EnvironmentFilterNamePredicate> LEGACY_MASK_PATTERNS =
            Stream.of(".*password.*", ".*credential.*", ".*certificate.*", ".*key.*", ".*secret.*", ".*token.*")
            .map(s -> regularExpressionPredicate(s, Pattern.CASE_INSENSITIVE))
            .collect(Collectors.toList());

    @Nullable
    private final Principal principal;
    private boolean allMasked;
    private final List<EnvironmentFilterNamePredicate> exclusions = new ArrayList<>();

    EnvironmentFilterSpecification(@Nullable Principal principal) {
        this.principal = principal;
        this.allMasked = true;
    }

    /**
     * @return The current {@link Principal} that is making the request (if any)
     */
    public @Nullable
    Principal getPrincipal() {
        return principal;
    }

    /**
     * Turn on global masking. Items can be unmasked by adding their {@link Pattern} to {@link EnvironmentFilterSpecification#exclude(EnvironmentFilterNamePredicate...)}.
     *
     * @return itself for chaining calls.
     */
    public @NotNull EnvironmentFilterSpecification maskAll() {
        allMasked = true;
        return this;
    }

    /**
     * Turn off global masking. Items can be masked by adding a matching {@link Predicate} to {@link EnvironmentFilterSpecification#exclude(EnvironmentFilterNamePredicate...)}.
     *
     * @return itself for chaining calls.
     */
    public @NotNull EnvironmentFilterSpecification maskNone() {
        allMasked = false;
        return this;
    }

    /**
     * Adds predicates to the list of known masks.  If the {@link EnvironmentFilterSpecification#maskAll()} flag is set
     * then this list will be used for exceptions (values in clear-text).  If the {@link EnvironmentFilterSpecification#maskNone()}
     * flag is set then this list will be those values that are masked.
     *
     * @param propertySourceNamePredicates one or more predicates to match against property names for masking.
     * @return itself for chaining calls.
     */
    public @NotNull EnvironmentFilterSpecification exclude(EnvironmentFilterNamePredicate... propertySourceNamePredicates) {
        exclusions.addAll(Arrays.asList(propertySourceNamePredicates));
        return this;
    }

    /**
     * Adds the masking rules from legacy pre 3.3.0.  Effectively the same as calling {@link EnvironmentFilterSpecification#maskNone()}
     * and adding predicates via {@link EnvironmentFilterSpecification#exclude(EnvironmentFilterNamePredicate...)} to mask anything containing the words
     * {@code password, credential, certificate, key, secret} or {@code token}.
     *
     * @return itself for chaining calls.
     */
    public @NotNull EnvironmentFilterSpecification legacyMasking() {
        allMasked = false;
        exclusions.addAll(LEGACY_MASK_PATTERNS);
        return this;
    }

    FilterResult test(String propertySourceName) {
        if (exclusions.stream().anyMatch(p -> p.test(propertySourceName))) {
            return allMasked ? FilterResult.PLAIN : FilterResult.MASK;
        }
        return allMasked ? FilterResult.MASK : FilterResult.PLAIN;
    }

    /**
     * Given a regular expression pattern string, generate a Predicate which matches to it.
     *
     * @param pattern A string representation of the Pattern for the matcher.
     * @return A {@code EnvironmentFilterNamePredicate} which when tested with a given property source name will
     * check it matches the passed pattern.
     */
    public static EnvironmentFilterNamePredicate regularExpressionPredicate(String pattern) {
        return regularExpressionPredicate(pattern, 0);
    }

    /**
     * Given a regular expression pattern string and Pattern flags, generate a Predicate which matches to it.
     *
     * @param pattern A string representation of the Pattern for the matcher.
     * @param flags Match flags, a bit mask that may include {@link Pattern#CASE_INSENSITIVE}, {@link Pattern#MULTILINE},
     * {@link Pattern#DOTALL}, {@link Pattern#UNICODE_CASE}, {@link Pattern#CANON_EQ}, {@link Pattern#UNIX_LINES}, {@link Pattern#LITERAL}, {@link Pattern#UNICODE_CHARACTER_CLASS} and {@link Pattern#COMMENTS}
     * @return A {@code EnvironmentFilterNamePredicate} which when tested with a given property source name will
     * check it matches the passed pattern.

     * @see Pattern#compile(String, int)
     */
    public static EnvironmentFilterNamePredicate regularExpressionPredicate(String pattern, int flags) {
        return s -> Pattern.compile(pattern, flags).matcher(s).matches();
    }

    /**
     * This is a type-alias for {@code Predicate<String>} so that varargs calls do not give an unchecked generic warning.
     *
     * @see Predicate
     */
    public interface EnvironmentFilterNamePredicate extends Predicate<String> {
    }

    enum FilterResult {
        HIDE,
        MASK,
        PLAIN
    }
}
