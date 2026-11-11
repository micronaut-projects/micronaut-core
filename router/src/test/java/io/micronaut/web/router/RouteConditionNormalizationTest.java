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
import io.micronaut.web.router.builder.RequestCondition;
import io.micronaut.web.router.builder.RequestConditions;
import io.micronaut.web.router.builder.ValueMatcher;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static io.micronaut.web.router.builder.RequestCondition.after;
import static io.micronaut.web.router.builder.RequestCondition.all;
import static io.micronaut.web.router.builder.RequestCondition.any;
import static io.micronaut.web.router.builder.RequestCondition.cookie;
import static io.micronaut.web.router.builder.RequestCondition.custom;
import static io.micronaut.web.router.builder.RequestCondition.header;
import static io.micronaut.web.router.builder.RequestCondition.host;
import static io.micronaut.web.router.builder.RequestCondition.method;
import static io.micronaut.web.router.builder.RequestCondition.not;
import static io.micronaut.web.router.builder.RequestCondition.query;
import static io.micronaut.web.router.builder.RequestCondition.remoteAddress;
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
        RequestCondition a = header("X-A");
        RequestCondition b = header("X-B");
        RequestCondition c = header("X-C");

        assertEquals(all(a, b, c), RequestConditions.normalize(all(a, all(b, all(c)))));
        assertEquals(any(a, b, c), RequestConditions.normalize(any(any(a, b), c)));
        assertEquals(all(a, any(b, c)), RequestConditions.normalize(all(a, any(b, c))), "an AnyOf in an AllOf stays");
        assertEquals(a, RequestConditions.normalize(all(all(a))), "a single part is the part");
    }

    @Test
    void doubleNegationsAndDuplicatesAreRemoved() {
        RequestCondition a = header("X-A");
        RequestCondition b = header("X-B");

        assertEquals(a, RequestConditions.normalize(new RequestCondition.Not(new RequestCondition.Not(a))));
        assertEquals(a, RequestConditions.normalize(not(not(a))));
        assertEquals(all(a, b), RequestConditions.normalize(all(a, b, a, all(b))));
        assertEquals(any(a, b), RequestConditions.normalize(any(a, b, a)));
        assertEquals(new RequestCondition.Header("X-A", equalTo("1")),
            RequestConditions.normalize(new RequestCondition.Header("X-A", ValueMatcher.not(ValueMatcher.not(equalTo("1"))))), "in a matcher too");
        assertEquals(new RequestCondition.Header("X-A", ValueMatcher.allOf(equalTo("1"), startsWith("1"))),
            RequestConditions.normalize(new RequestCondition.Header("X-A", ValueMatcher.allOf(startsWith("1"), ValueMatcher.allOf(equalTo("1"), startsWith("1"))))),
            "a matcher is flattened, deduplicated and ordered too");
    }

    @Test
    void combinationsThatAreAlwaysOrNeverMetAreFolded() {
        RequestCondition a = header("X-A");

        assertEquals(RequestConditions.ALWAYS, RequestConditions.normalize(all()));
        assertEquals(RequestConditions.NEVER, RequestConditions.normalize(any()));
        assertEquals(RequestConditions.NEVER, RequestConditions.normalize(all(a, any())), "a part never met");
        assertEquals(RequestConditions.ALWAYS, RequestConditions.normalize(any(a, all())), "a part always met");
        assertEquals(a, RequestConditions.normalize(all(a, all())));
        assertEquals(RequestConditions.NEVER, RequestConditions.normalize(not(all())));
        assertEquals(RequestConditions.ALWAYS, RequestConditions.normalize(not(any())));
        assertEquals(RequestConditions.ALWAYS, RequestConditions.normalizeAll(List.of()));
    }

    @Test
    void thePartsAreOrderedCheapestFirstStably() {
        Predicate<HttpRequest<?>> first = request -> true;
        Predicate<HttpRequest<?>> second = request -> true;
        RequestCondition regexHeader = header("X-R", regex("\\d+"));
        RequestCondition affix = header("X-S", startsWith("a"));
        RequestCondition lookup = query("q", oneOf("a", "b"));
        RequestCondition presence = cookie("c");
        RequestCondition methods = method(HttpMethod.GET);
        RequestCondition hostCondition = host(endsWith(".example.com"));
        RequestCondition address = remoteAddress("10.0.0.0/8");
        RequestCondition time = after(Instant.EPOCH);

        RequestCondition normalized = RequestConditions.normalize(all(custom(first), regexHeader, hostCondition, affix,
            custom(second), address, lookup, time, presence, methods));

        assertEquals(List.of(methods, lookup, time, presence, affix, regexHeader, hostCondition, address,
                new RequestCondition.Custom(first), new RequestCondition.Custom(second)),
            RequestConditions.conjuncts(normalized),
            "the method, the lookups and the time, the affixes, the regular expressions and the resolved values, then the lambdas in order");
        assertEquals(any(methods, affix, new RequestCondition.Custom(first)),
            RequestConditions.normalize(any(custom(first), affix, methods)), "an AnyOf too");
        assertEquals(all(header("X-A", equalTo("1")), not(header("X-B", contains("x")))),
            RequestConditions.normalize(all(not(header("X-B", contains("x"))), header("X-A", equalTo("1")))), "a negation costs what it negates");
    }

    @Test
    void theEvaluationStopsAtThePartThatDecides() {
        List<String> evaluated = new ArrayList<>();
        RequestCondition condition = RequestConditions.normalize(all(recorded(evaluated, "lambda"), header("X-A"), method(HttpMethod.POST)));

        assertFalse(meets(condition, HttpRequest.GET("/x").header("X-A", "1")));
        assertEquals(List.of(), evaluated, "the method decides before the lambda runs");
        assertTrue(meets(condition, HttpRequest.POST("/x", "").header("X-A", "1")));
        assertEquals(List.of("lambda"), evaluated);

        evaluated.clear();
        RequestCondition either = RequestConditions.normalize(any(recorded(evaluated, "lambda"), header("X-A")));
        assertTrue(meets(either, HttpRequest.GET("/x").header("X-A", "1")));
        assertEquals(List.of(), evaluated, "the header decides before the lambda runs");
    }

    private static RequestCondition recorded(List<String> evaluated, String name) {
        return custom(request -> {
            evaluated.add(name);
            return true;
        });
    }

    private static boolean meets(io.micronaut.web.router.builder.RequestCondition condition, io.micronaut.http.HttpRequest<?> request) {
        return io.micronaut.web.router.builder.RequestConditions.matches(condition, request, RouteConditionContext.fallback());
    }
}
