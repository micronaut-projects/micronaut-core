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
package io.micronaut.web.router;

import io.micronaut.web.router.builder.RouteConditions;
import io.micronaut.web.router.builder.ValueMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static io.micronaut.web.router.builder.ValueMatcher.allOf;
import static io.micronaut.web.router.builder.ValueMatcher.anyOf;
import static io.micronaut.web.router.builder.ValueMatcher.contains;
import static io.micronaut.web.router.builder.ValueMatcher.endsWith;
import static io.micronaut.web.router.builder.ValueMatcher.equalTo;
import static io.micronaut.web.router.builder.ValueMatcher.oneOf;
import static io.micronaut.web.router.builder.ValueMatcher.present;
import static io.micronaut.web.router.builder.ValueMatcher.regex;
import static io.micronaut.web.router.builder.ValueMatcher.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link ValueMatcher} matchers and their combinations.
 */
class ValueMatcherTest {

    @Test
    void equalValues() {
        assertTrue(ValueMatcher.equalTo("csv").matches("csv"));
        assertFalse(ValueMatcher.equalTo("csv").matches("CSV"), "case-sensitive");
        assertFalse(ValueMatcher.equalTo("csv").matches("csvx"));
        assertFalse(ValueMatcher.equalTo("csv").matches(null), "an absent value");
        assertTrue(ValueMatcher.equalTo("csv").ignoringCase().matches("CsV"));
        assertTrue(ValueMatcher.equalTo("").matches(""));
    }

    @Test
    void affixes() {
        assertTrue(startsWith("de").matches("debug"));
        assertFalse(startsWith("de").matches("d"));
        assertFalse(startsWith("DE").matches("debug"));
        assertTrue(startsWith("DE").ignoringCase().matches("debug"));

        assertTrue(endsWith(".example.com").matches("api.example.com"));
        assertFalse(endsWith(".example.com").matches("example.com"));
        assertTrue(endsWith(".EXAMPLE.com").ignoringCase().matches("api.example.COM"));

        assertTrue(contains("bet").matches("alphabeta"));
        assertTrue(contains("").matches("x"), "an empty part is in every value");
        assertFalse(contains("gamma").matches("alphabeta"));
        assertTrue(contains("BET").ignoringCase().matches("alphabeta"));
        assertFalse(contains("x").matches(null));
    }

    @Test
    void oneOfValues() {
        assertTrue(oneOf("north", "south").matches("south"));
        assertFalse(oneOf("north", "south").matches("South"));
        assertTrue(oneOf("north", "south").ignoringCase().matches("SOUTH"));
        assertTrue(oneOf(List.of("a", "b")).matches("a"));
        assertFalse(oneOf("north").matches(null));
        assertThrows(IllegalArgumentException.class, ValueMatcher::oneOf);
        assertThrows(IllegalArgumentException.class, () -> oneOf(Set.of()));
    }

    @Test
    void regularExpressionsMatchTheWholeValue() {
        assertTrue(regex("[a-c]").matches("b"));
        assertFalse(regex("[a-c]").matches("bb"), "the whole value");
        assertFalse(regex("[a-c]").matches("B"));
        assertTrue(regex("[a-c]").ignoringCase().matches("B"));
        assertTrue(regex(Pattern.compile("v\\d+")).matches("v42"));
        assertFalse(regex(".*").matches(null), "an absent value is not an empty one");
        assertEquals(regex("[a-c]"), regex("[a-c]"), "equal by the expression and the flags");
        assertEquals(regex("[a-c]").hashCode(), regex("[a-c]").hashCode());
        assertNotEquals(regex("[a-c]"), regex("[a-c]").ignoringCase());
    }

    @Test
    void presentAndAbsentValues() {
        assertTrue(present().matches(""));
        assertFalse(present().matches(null));
        assertTrue(present().negate().matches(null), "a negated matcher matches an absent value");
        assertFalse(equalTo("a").matches(null));
        assertTrue(equalTo("a").negate().matches(null));
        assertSame(present(), present().ignoringCase());
    }

    @Test
    void combinations() {
        ValueMatcher beta = startsWith("beta").and(endsWith("-rc").negate());
        assertTrue(beta.matches("beta-1"));
        assertFalse(beta.matches("beta-rc"));
        assertFalse(beta.matches("alpha"));

        ValueMatcher either = equalTo("a").or(regex("x\\d"));
        assertTrue(either.matches("a"));
        assertTrue(either.matches("x1"));
        assertFalse(either.matches("b"));

        assertTrue(allOf().matches("anything"), "no matchers: every value");
        assertTrue(allOf().matches(null), "and an absent one");
        assertFalse(anyOf().matches("anything"), "no matchers: no value");
        assertTrue(allOf(present(), contains("-")).matches("a-b"));
        assertTrue(anyOf(present().negate(), equalTo("on")).matches(null));

        assertEquals(equalTo("a"), equalTo("a").negate().negate());
        assertInstanceOf(ValueMatcher.Not.class, ValueMatcher.not(equalTo("a")));
        ValueMatcher ignoring = allOf(equalTo("A"), anyOf(startsWith("A"), ValueMatcher.not(endsWith("Z")))).ignoringCase();
        assertTrue(ignoring.matches("a"));
    }

    @Test
    void aCharSequenceIsComparedInPlace() {
        StringBuilder value = new StringBuilder("Beta-Channel");
        assertTrue(startsWith("beta").ignoringCase().matches(value));
        assertTrue(endsWith("Channel").matches(value));
        assertTrue(contains("a-C").matches(value));
        assertTrue(equalTo("beta-channel").ignoringCase().matches(value));
        assertTrue(oneOf("x", "Beta-Channel").matches(value));
        assertTrue(oneOf("x", "beta-channel").ignoringCase().matches(value));
        assertTrue(regex("[A-Z]\\w+-\\w+").matches(value));

        // a region of a value, e.g. of a cookie in the Cookie header
        String header = "a=1; variant=beta; z=9";
        int start = header.indexOf("beta");
        assertTrue(RouteConditions.matches(equalTo("beta"), header, start, start + 4));
        assertTrue(RouteConditions.matches(oneOf("alpha", "beta"), header, start, start + 4));
        assertTrue(RouteConditions.matches(regex("b\\w+"), header, start, start + 4), "the region bounds the expression");
        assertFalse(RouteConditions.matches(endsWith("9"), header, start, start + 4));
    }

    @Test
    void theRecordsRejectMissingParts() {
        assertThrows(NullPointerException.class, () -> equalTo(null));
        assertThrows(NullPointerException.class, () -> new ValueMatcher.Not(null));
        assertThrows(NullPointerException.class, () -> regex((String) null));
    }
}
