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
package io.micronaut.http.server.tck;

import io.micronaut.core.annotation.Experimental;

import java.util.Arrays;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP Response's body assertions.
 */
@Experimental
public final class BodyAssertion<T> {

    private final Class<T> bodyType;
    private final T expected;
    private final BiFunction<T, T, Boolean> evaluator;

    private BodyAssertion(Class<T> bodyType, T expected, BiFunction<T, T, Boolean> evaluator) {
        this.bodyType = bodyType;
        this.expected = expected;
        this.evaluator = evaluator;
    }

    /**
     * @return a Builder;
     */
    public static BodyAssertion.Builder builder() {
        return new BodyAssertion.Builder();
    }

    /**
     * Evaluates the HTTP Response Body.
     *
     * @param body The HTTP Response Body
     */
    public void evaluate(T body) {
        assertTrue(this.evaluator.apply(expected, body));
    }

    /**
     * @return The expected body type
     */
    public Class<T> getBodyType() {
        return bodyType;
    }

    public interface AssertionBuilder<T> {

        /**
         * @return a body assertion which verifiers the HTTP Response's body contains the expected body
         */
        BodyAssertion<T> contains();

        /**
         * @return a body assertion which verifiers the HTTP Response's body is equals to the expected body
         */
        BodyAssertion<T> equals();
    }

    /**
     * BodyAssertion Builder.
     */
    public static class Builder {

        /**
         * @param expected Expected Body
         * @return The Builder
         */
        public AssertionBuilder<String> body(String expected) {
            return new StringBodyAssertionBuilder(expected);
        }

        /**
         * @param expected Expected Body
         * @return The Builder
         */
        public AssertionBuilder<byte[]> body(byte[] expected) {
            return new ByteArrayBodyAssertionBuilder(expected);
        }
    }

    public static class StringBodyAssertionBuilder extends BodyAssertion.Builder implements AssertionBuilder<String> {

        private final String body;

        public StringBodyAssertionBuilder(String expected) {
            this.body = expected;
        }

        /**
         * @return a body assertion which verifiers the HTTP Response's body contains the expected body
         */
        public BodyAssertion<String> contains() {
            return new BodyAssertion<>(String.class, this.body, (expected, body) -> body.contains(expected));
        }

        /**
         * @return a body assertion which verifiers the HTTP Response's body is equals to the expected body
         */
        public BodyAssertion<String> equals() {
            return new BodyAssertion<>(String.class, this.body, (expected, body) -> body.equals(expected));
        }
    }

    public static class ByteArrayBodyAssertionBuilder extends BodyAssertion.Builder implements BodyAssertion.AssertionBuilder<byte[]> {

        private final byte[] body;

        public ByteArrayBodyAssertionBuilder(byte[] expected) {
            this.body = expected;
        }

        /**
         * @return a body assertion which verifiers the HTTP Response's body contains the expected body
         */
        public BodyAssertion<byte[]> contains() {
            return new BodyAssertion<>(byte[].class, this.body, (expected, body) -> {
                for (int i = 0; i <= body.length - expected.length; i++) {
                    int j = 0;
                    while (j < expected.length && body[i + j] == expected[j]) {
                        j++;
                    }
                    if (j == expected.length) {
                        return true;
                    }
                }
                return false;
            });
        }

        /**
         * @return a body assertion which verifiers the HTTP Response's body is equals to the expected body
         */
        public BodyAssertion<byte[]> equals() {
            return new BodyAssertion<>(byte[].class, this.body, (expected, body) -> Arrays.equals(body, expected));
        }
    }
}
