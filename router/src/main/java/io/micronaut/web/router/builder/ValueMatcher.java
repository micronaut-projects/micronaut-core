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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A declarative matcher of a value: of a header, a query parameter, a cookie or the host of a
 * request, see {@link RouteCondition}, or of a path variable, see
 * {@link RouteSpec#constrain(String, ValueMatcher)}. A matcher is data: the router can read,
 * combine, reorder and index it, which a lambda does not allow.
 *
 * <pre>{@code
 * import static io.micronaut.web.router.builder.ValueMatcher.*;
 *
 * routes.GET("/search")
 *     .where(RouteCondition.header("X-Channel", oneOf("beta", "canary").ignoringCase()))
 *     .handle(betaHandler);
 * routes.GET("/files/{name}")
 *     .constrain("name", startsWith(".").negate())
 *     .handle(filesHandler);
 * }</pre>
 *
 * <p>A matcher is given the value as it is, not decoded nor trimmed any further than the source
 * of the value does, and compares it without copying it. It is given {@code null} for a value
 * that is absent: only {@link #present()} negated, or a combination that accepts it, matches an
 * absent value.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface ValueMatcher {

    /**
     * A value equal to one, e.g. {@code equalTo("csv")}.
     *
     * @param value The value
     * @return The matcher, case-sensitive
     */
    static ValueMatcher equalTo(String value) {
        return new Equals(value, false);
    }

    /**
     * A value that starts with a prefix.
     *
     * @param prefix The prefix
     * @return The matcher, case-sensitive
     */
    static ValueMatcher startsWith(String prefix) {
        return new StartsWith(prefix, false);
    }

    /**
     * A value that ends with a suffix, e.g. {@code endsWith(".example.com")}.
     *
     * @param suffix The suffix
     * @return The matcher, case-sensitive
     */
    static ValueMatcher endsWith(String suffix) {
        return new EndsWith(suffix, false);
    }

    /**
     * A value that contains a part.
     *
     * @param part The part
     * @return The matcher, case-sensitive
     */
    static ValueMatcher contains(String part) {
        return new Contains(part, false);
    }

    /**
     * A value equal to one of the values, e.g. {@code oneOf("north", "south")}.
     *
     * @param values The values, at least one
     * @return The matcher, case-sensitive
     */
    static ValueMatcher oneOf(String... values) {
        Objects.requireNonNull(values, "values");
        return oneOf(Arrays.asList(values));
    }

    /**
     * A value equal to one of the values, copied now.
     *
     * @param values The values, at least one
     * @return The matcher, case-sensitive
     */
    static ValueMatcher oneOf(Collection<String> values) {
        Objects.requireNonNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("A value is required");
        }
        return new OneOf(Set.copyOf(values), false);
    }

    /**
     * A value that the regular expression matches as a whole, e.g. {@code regex("[a-c]")}.
     *
     * @param regex The regular expression, see {@link Pattern}
     * @return The matcher
     */
    static ValueMatcher regex(String regex) {
        return new Regex(Pattern.compile(Objects.requireNonNull(regex, "regex")));
    }

    /**
     * A value that the pattern matches as a whole.
     *
     * @param pattern The pattern
     * @return The matcher
     */
    static ValueMatcher regex(Pattern pattern) {
        return new Regex(pattern);
    }

    /**
     * A value that is present, whatever it is, e.g. a header of the name.
     *
     * @return The matcher
     */
    static ValueMatcher present() {
        return Present.INSTANCE;
    }

    /**
     * A value the matcher does not match: an absent value too, unless the matcher accepts it.
     *
     * @param matcher The matcher
     * @return The matcher
     */
    static ValueMatcher not(ValueMatcher matcher) {
        return new Not(matcher);
    }

    /**
     * A value that every matcher matches.
     *
     * @param matchers The matchers
     * @return The matcher, which matches any value, an absent value too, if there are none
     */
    static ValueMatcher allOf(ValueMatcher... matchers) {
        return new AllOf(List.of(matchers));
    }

    /**
     * A value that one of the matchers matches.
     *
     * @param matchers The matchers
     * @return The matcher, which matches no value if there are none
     */
    static ValueMatcher anyOf(ValueMatcher... matchers) {
        return new AnyOf(List.of(matchers));
    }

    /**
     * Whether the matcher matches a value. The value is compared in place: it is not copied.
     *
     * @param value The value, or {@code null} if it is absent
     * @return Whether it matches
     */
    default boolean matches(@Nullable CharSequence value) {
        return RouteConditions.matches(this, value);
    }

    /**
     * This matcher, comparing the values ignoring case: a regular expression is compiled again
     * with {@link Pattern#CASE_INSENSITIVE} and {@link Pattern#UNICODE_CASE}.
     *
     * @return The matcher
     */
    default ValueMatcher ignoringCase() {
        return switch (this) {
            case Equals equals -> new Equals(equals.value(), true);
            case StartsWith startsWith -> new StartsWith(startsWith.prefix(), true);
            case EndsWith endsWith -> new EndsWith(endsWith.suffix(), true);
            case Contains contains -> new Contains(contains.part(), true);
            case OneOf oneOf -> new OneOf(oneOf.values(), true);
            case Regex regex -> new Regex(Pattern.compile(regex.pattern().pattern(),
                regex.pattern().flags() | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            case Present present -> present;
            case Not not -> new Not(not.matcher().ignoringCase());
            case AllOf allOf -> new AllOf(ignoringCase(allOf.matchers()));
            case AnyOf anyOf -> new AnyOf(ignoringCase(anyOf.matchers()));
        };
    }

    /**
     * @return A matcher of the values this matcher does not match
     */
    default ValueMatcher negate() {
        return this instanceof Not not ? not.matcher() : new Not(this);
    }

    /**
     * @param other Another matcher
     * @return A matcher of the values both matchers match
     */
    default ValueMatcher and(ValueMatcher other) {
        return new AllOf(List.of(this, other));
    }

    /**
     * @param other Another matcher
     * @return A matcher of the values one of the matchers matches
     */
    default ValueMatcher or(ValueMatcher other) {
        return new AnyOf(List.of(this, other));
    }

    private static List<ValueMatcher> ignoringCase(List<ValueMatcher> matchers) {
        List<ValueMatcher> result = new ArrayList<>(matchers.size());
        for (ValueMatcher matcher : matchers) {
            result.add(matcher.ignoringCase());
        }
        return result;
    }

    /**
     * A value equal to another.
     *
     * @param value      The value
     * @param ignoreCase Whether the case is ignored
     */
    record Equals(String value, boolean ignoreCase) implements ValueMatcher {
        public Equals {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A value that starts with a prefix.
     *
     * @param prefix     The prefix
     * @param ignoreCase Whether the case is ignored
     */
    record StartsWith(String prefix, boolean ignoreCase) implements ValueMatcher {
        public StartsWith {
            Objects.requireNonNull(prefix, "prefix");
        }
    }

    /**
     * A value that ends with a suffix.
     *
     * @param suffix     The suffix
     * @param ignoreCase Whether the case is ignored
     */
    record EndsWith(String suffix, boolean ignoreCase) implements ValueMatcher {
        public EndsWith {
            Objects.requireNonNull(suffix, "suffix");
        }
    }

    /**
     * A value that contains a part.
     *
     * @param part       The part
     * @param ignoreCase Whether the case is ignored
     */
    record Contains(String part, boolean ignoreCase) implements ValueMatcher {
        public Contains {
            Objects.requireNonNull(part, "part");
        }
    }

    /**
     * A value equal to one of the values.
     *
     * @param values     The values, copied
     * @param ignoreCase Whether the case is ignored
     */
    record OneOf(Set<String> values, boolean ignoreCase) implements ValueMatcher {
        public OneOf {
            values = Set.copyOf(values);
        }
    }

    /**
     * A value that a pattern matches as a whole. Two matchers of the same regular expression and
     * flags are equal.
     *
     * @param pattern The pattern
     */
    record Regex(Pattern pattern) implements ValueMatcher {
        public Regex {
            Objects.requireNonNull(pattern, "pattern");
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Regex that && pattern.flags() == that.pattern.flags() && pattern.pattern().equals(that.pattern.pattern());
        }

        @Override
        public int hashCode() {
            return 31 * pattern.pattern().hashCode() + pattern.flags();
        }
    }

    /**
     * A value that is present.
     */
    record Present() implements ValueMatcher {
        private static final Present INSTANCE = new Present();
    }

    /**
     * A value another matcher does not match.
     *
     * @param matcher The negated matcher
     */
    record Not(ValueMatcher matcher) implements ValueMatcher {
        public Not {
            Objects.requireNonNull(matcher, "matcher");
        }
    }

    /**
     * A value that every matcher matches.
     *
     * @param matchers The matchers, copied
     */
    record AllOf(List<ValueMatcher> matchers) implements ValueMatcher {
        public AllOf {
            matchers = List.copyOf(matchers);
        }
    }

    /**
     * A value that one of the matchers matches.
     *
     * @param matchers The matchers, copied
     */
    record AnyOf(List<ValueMatcher> matchers) implements ValueMatcher {
        public AnyOf {
            matchers = List.copyOf(matchers);
        }
    }
}
