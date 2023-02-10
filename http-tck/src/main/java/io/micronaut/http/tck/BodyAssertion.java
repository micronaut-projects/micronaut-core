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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.client.HttpClient;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP Response's body assertions.
 *
 * @param <T> The body type
 */
@Experimental
public final class BodyAssertion<T,E> {

    private final Class<T> bodyType;
    private final Class<E> errorType;
    private final T expected;
    private final BiPredicate<T, T> evaluator;

    private BodyAssertion(Class<T> bodyType,
                          Class<E> errorType,
                          T expected, BiPredicate<T, T> evaluator) {
        this.bodyType = bodyType;
        this.errorType = errorType;
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
    @SuppressWarnings("java:S5960") // Assertion is the whole point of this method
    public void evaluate(T body) {
        assertTrue(this.evaluator.test(expected, body));
    }

    /**
     * @return The expected body type
     */
    public Class<T> getBodyType() {
        return bodyType;
    }

    @Nullable
    public Class<E> getErrorType() {
        return errorType;
    }

    /**
     * The interface for typed BodyAssertion Builders.
     *
     * @param <T> The body type
     */
    public interface AssertionBuilder<T, E> {

        /**
         * @return a body assertion which verifiers the HTTP Response's body contains the expected body
         */
        BodyAssertion<T, E> contains();

        /**
         * @return a body assertion which verifiers the HTTP Response's body is equals to the expected body
         */
        BodyAssertion<T, E> equals();
    }

    /**
     * BodyAssertion Builder.
     */
    public static class Builder {

        /**
         * @param expected Expected Body
         * @return The Builder
         */
        public AssertionBuilder<String, String> body(String expected) {
            return new StringBodyAssertionBuilder(expected);
        }

        /**
         * @param expected Expected Body
         * @return The Builder
         */
        public AssertionBuilder<byte[], byte[]> body(byte[] expected) {
            return new ByteArrayBodyAssertionBuilder(expected);
        }
    }

    /**
     * String BodyAssertion Builder.
     */
    public static class StringBodyAssertionBuilder extends BodyAssertion.Builder implements AssertionBuilder<String, String> {

        private final String body;

        public StringBodyAssertionBuilder(String expected) {
            this.body = expected;
        }

        /**
         * @return a body assertion which verifiers the HTTP Response's body contains the expected body
         */
        public BodyAssertion<String, String> contains() {
            return new BodyAssertion<>(String.class, String.class, this.body, (required, received) -> received.contains(required));
        }

        /**
         * @return a body assertion which verifiers the HTTP Response's body is equals to the expected body
         */
        public BodyAssertion<String, String> equals() {
            return new BodyAssertion<>(String.class, String.class, this.body, (required, received) -> received.equals(required));
        }
    }

    /**
     * Byte Array BodyAssertion Builder.
     */
    public static class ByteArrayBodyAssertionBuilder extends BodyAssertion.Builder implements BodyAssertion.AssertionBuilder<byte[],byte[]> {

        private final byte[] body;

        public ByteArrayBodyAssertionBuilder(byte[] expected) {
            this.body = expected;
        }

        /**
         * @return a body assertion which verifiers the HTTP Response's body contains the expected body
         */
        public BodyAssertion<byte[], byte[]> contains() {
            return new BodyAssertion<>(byte[].class, byte[].class, this.body, (required, received) -> {
                throw new AssertionError("Not implemented yet!");
            });
        }

        /**
         * @return a body assertion which verifiers the HTTP Response's body is equals to the expected body
         */
        public BodyAssertion<byte[], byte[]> equals() {
            return new BodyAssertion<>(byte[].class, byte[].class, this.body, (required, received) -> Arrays.equals(received, required));
        }
    }
}
