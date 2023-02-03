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
package io.micronaut.http.tck;

import io.micronaut.core.annotation.Experimental;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP Reponse's body assertions.
 */
@Experimental
public final class BodyAssertion {
    private final String expected;
    private final BiFunction<String, String, Boolean> evaluator;

    private BodyAssertion(String expected, BiFunction<String, String, Boolean> evaluator) {
        this.expected = expected;
        this.evaluator = evaluator;
    }

    /**
     * Evaluates the HTTP Response Body.
     * @param body The HTTP Response Body
     */
    public void evaluate(String body) {
        assertTrue(this.evaluator.apply(expected, body));
    }

    /**
     *
     * @return a Builder;
     */
    public static BodyAssertion.Builder builder() {
        return new BodyAssertion.Builder();
    }

    /**
     * BodyAssertion Builder.
     */
    public static class Builder {

        private String body;

        /**
         *
         * @param expected Expected Body
         * @return The Builder
         */
        public Builder body(String expected) {
            this.body = expected;
            return this;
        }

        /**
         *
         * @return a body assertion which verifiers the HTTP Response's body contains the expected body
         */
        public BodyAssertion contains() {
            return new BodyAssertion(this.body, (expected, body) -> body.contains(expected));
        }

        /**
         *
         * @return a body assertion which verifiers the HTTP Response's body is equals to the expected body
         */
        public BodyAssertion equals() {
            return new BodyAssertion(this.body, (expected, body) -> body.equals(expected));
        }
    }
}
