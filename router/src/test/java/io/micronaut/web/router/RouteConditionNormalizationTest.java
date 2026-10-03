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

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.builder.RouteCondition;
import io.micronaut.web.router.builder.RouteConditions;
import io.micronaut.web.router.builder.ValueMatcher;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static io.micronaut.web.router.builder.RouteCondition.after;
import static io.micronaut.web.router.builder.RouteCondition.all;
import static io.micronaut.web.router.builder.RouteCondition.any;
import static io.micronaut.web.router.builder.RouteCondition.cookie;
import static io.micronaut.web.router.builder.RouteCondition.custom;
import static io.micronaut.web.router.builder.RouteCondition.header;
import static io.micronaut.web.router.builder.RouteCondition.host;
import static io.micronaut.web.router.builder.RouteCondition.method;
import static io.micronaut.web.router.builder.RouteCondition.not;
import static io.micronaut.web.router.builder.RouteCondition.query;
import static io.micronaut.web.router.builder.RouteCondition.remoteAddress;
import static io.micronaut.web.router.builder.ValueMatcher.contains;
import static io.micronaut.web.router.builder.ValueMatcher.endsWith;
import static io.micronaut.web.router.builder.ValueMatcher.equalTo;
import static io.micronaut.web.router.builder.ValueMatcher.oneOf;
import static io.micronaut.web.router.builder.ValueMatcher.regex;
import static io.micronaut.web.router.builder.ValueMatcher.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The normalization of the conditions of a route: flattened, folded, without double negations
 * and duplicates, ordered cheapest first, and evaluated until one decides.
 */
class RouteConditionNormalizationTest {

    @Test
    void nestedCombinationsAreFlattened() {
        RouteCondition a = header("X-A");
        RouteCondition b = header("X-B");
        RouteCondition c = header("X-C");

        assertEquals(all(a, b, c), RouteConditions.normalize(all(a, all(b, all(c)))));
        assertEquals(any(a, b, c), RouteConditions.normalize(any(any(a, b), c)));
        assertEquals(all(a, any(b, c)), RouteConditions.normalize(all(a, any(b, c))), "an AnyOf in an AllOf stays");
        assertEquals(a, RouteConditions.normalize(all(all(a))), "a single part is the part");
    }

    @Test
    void doubleNegationsAndDuplicatesAreRemoved() {
        RouteCondition a = header("X-A");
        RouteCondition b = header("X-B");

        assertEquals(a, RouteConditions.normalize(new RouteCondition.Not(new RouteCondition.Not(a))));
        assertEquals(a, RouteConditions.normalize(not(not(a))));
        assertEquals(all(a, b), RouteConditions.normalize(all(a, b, a, all(b))));
        assertEquals(any(a, b), RouteConditions.normalize(any(a, b, a)));
        assertEquals(new RouteCondition.Header("X-A", equalTo("1")),
            RouteConditions.normalize(new RouteCondition.Header("X-A", ValueMatcher.not(ValueMatcher.not(equalTo("1"))))), "in a matcher too");
        assertEquals(new RouteCondition.Header("X-A", ValueMatcher.allOf(equalTo("1"), startsWith("1"))),
            RouteConditions.normalize(new RouteCondition.Header("X-A", ValueMatcher.allOf(startsWith("1"), ValueMatcher.allOf(equalTo("1"), startsWith("1"))))),
            "a matcher is flattened, deduplicated and ordered too");
    }

    @Test
    void combinationsThatAreAlwaysOrNeverMetAreFolded() {
        RouteCondition a = header("X-A");

        assertEquals(RouteConditions.ALWAYS, RouteConditions.normalize(all()));
        assertEquals(RouteConditions.NEVER, RouteConditions.normalize(any()));
        assertEquals(RouteConditions.NEVER, RouteConditions.normalize(all(a, any())), "a part never met");
        assertEquals(RouteConditions.ALWAYS, RouteConditions.normalize(any(a, all())), "a part always met");
        assertEquals(a, RouteConditions.normalize(all(a, all())));
        assertEquals(RouteConditions.NEVER, RouteConditions.normalize(not(all())));
        assertEquals(RouteConditions.ALWAYS, RouteConditions.normalize(not(any())));
        assertEquals(RouteConditions.ALWAYS, RouteConditions.normalizeAll(List.of()));
    }

    @Test
    void thePartsAreOrderedCheapestFirstStably() {
        Predicate<HttpRequest<?>> first = request -> true;
        Predicate<HttpRequest<?>> second = request -> true;
        RouteCondition regexHeader = header("X-R", regex("\\d+"));
        RouteCondition affix = header("X-S", startsWith("a"));
        RouteCondition lookup = query("q", oneOf("a", "b"));
        RouteCondition presence = cookie("c");
        RouteCondition methods = method(HttpMethod.GET);
        RouteCondition hostCondition = host(endsWith(".example.com"));
        RouteCondition address = remoteAddress("10.0.0.0/8");
        RouteCondition time = after(Instant.EPOCH);

        RouteCondition normalized = RouteConditions.normalize(all(custom(first), regexHeader, hostCondition, affix,
            custom(second), address, lookup, time, presence, methods));

        assertEquals(List.of(methods, lookup, time, presence, affix, regexHeader, hostCondition, address,
                new RouteCondition.Custom(first), new RouteCondition.Custom(second)),
            RouteConditions.conjuncts(normalized),
            "the method, the lookups and the time, the affixes, the regular expressions and the resolved values, then the lambdas in order");
        assertEquals(any(methods, affix, new RouteCondition.Custom(first)),
            RouteConditions.normalize(any(custom(first), affix, methods)), "an AnyOf too");
        assertEquals(all(header("X-A", equalTo("1")), not(header("X-B", contains("x")))),
            RouteConditions.normalize(all(not(header("X-B", contains("x"))), header("X-A", equalTo("1")))), "a negation costs what it negates");
    }

    @Test
    void theEvaluationStopsAtThePartThatDecides() {
        List<String> evaluated = new ArrayList<>();
        RouteCondition condition = RouteConditions.normalize(all(record(evaluated, "lambda"), header("X-A"), method(HttpMethod.POST)));

        assertFalse(condition.test(HttpRequest.GET("/x").header("X-A", "1")));
        assertEquals(List.of(), evaluated, "the method decides before the lambda runs");
        assertTrue(condition.test(HttpRequest.POST("/x", "").header("X-A", "1")));
        assertEquals(List.of("lambda"), evaluated);

        evaluated.clear();
        RouteCondition either = RouteConditions.normalize(any(record(evaluated, "lambda"), header("X-A")));
        assertTrue(either.test(HttpRequest.GET("/x").header("X-A", "1")));
        assertEquals(List.of(), evaluated, "the header decides before the lambda runs");
    }

    private static RouteCondition record(List<String> evaluated, String name) {
        return custom(request -> {
            evaluated.add(name);
            return true;
        });
    }
}
