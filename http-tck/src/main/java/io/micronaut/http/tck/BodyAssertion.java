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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP Response's body assertions.
 *
 * @param <T> The body type
 * @param <E> The error type
 */
@Experimental
public final class BodyAssertion<T, E> {
    private static final Logger LOG = LoggerFactory.getLogger(BodyAssertion.class);

    private final Class<T> bodyType;
    private final Class<E> errorType;
    private final T expected;
    private final BodyEvaluator<T> evaluator;

    private BodyAssertion(Class<T> bodyType,
                          Class<E> errorType,
                          T expected,
                          BodyEvaluator<T> evaluator
    ) {
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
        assertTrue(this.evaluator.test(expected, body), () -> this.evaluator.message(expected, body));
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

    private enum EvaluatorType {
        EQUAL,
        CONTAIN,
    }

    /**
     * The interface for typed BodyAssertion Builders.
     *
     * @param <T> The body type
     * @param <E> The error type
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

    private interface BodyEvaluator<T> extends BiPredicate<T, T> {

        EvaluatorType type();

        default String render(T value) {
            return String.valueOf(value);
        }

        default String message(T expected, T actual) {
            return "Expected received body of '" + render(actual) + "' to " + type().name().toLowerCase() + " '" + render(expected) + "'";
        }
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
            return new BodyAssertion<>(String.class, String.class, this.body, new StringEvaluator(EvaluatorType.CONTAIN));
        }

        /**
         * @return a body assertion which verifiers the HTTP Response's body is equals to the expected body
         */
        public BodyAssertion<String, String> equals() {
            return new BodyAssertion<>(String.class, String.class, this.body, new StringEvaluator(EvaluatorType.EQUAL));
        }
    }

    /**
     * Byte Array BodyAssertion Builder.
     */
    public static class ByteArrayBodyAssertionBuilder extends BodyAssertion.Builder implements BodyAssertion.AssertionBuilder<byte[], byte[]> {

        private final byte[] body;

        public ByteArrayBodyAssertionBuilder(byte[] expected) {
            this.body = expected;
        }

        /**
         * @return a body assertion which verifiers the HTTP Response's body contains the expected body
         */
        public BodyAssertion<byte[], byte[]> contains() {
            return new BodyAssertion<>(byte[].class, byte[].class, this.body, new ByteArrayEvaluator(EvaluatorType.CONTAIN));
        }

        /**
         * @return a body assertion which verifiers the HTTP Response's body is equals to the expected body
         */
        public BodyAssertion<byte[], byte[]> equals() {
            return new BodyAssertion<>(byte[].class, byte[].class, this.body, new ByteArrayEvaluator(EvaluatorType.EQUAL));
        }
    }

    private record StringEvaluator(EvaluatorType type) implements BodyEvaluator<String> {

        @Override
        public boolean test(String expected, String received) {
            return switch (type) {
                case EQUAL -> received.equals(expected);
                case CONTAIN -> received.contains(expected);
            };
        }
    }

    private record ByteArrayEvaluator(EvaluatorType type) implements BodyEvaluator<byte[]> {

        @Override
        public String render(byte[] value) {
            if (value == null) {
                return "null";
            }
            String firstTen = IntStream.range(0, value.length)
                .map(i -> value[i] & 0xff)
                .mapToObj(i -> String.format("%02x", i))
                .limit(10)
                .collect(Collectors.joining(", ", "", "..."));
            return "ByteArray(length=" + value.length + ", [" + firstTen + "])";
        }

        @Override
        public boolean test(byte[] expected, byte[] received) {
            return switch (type) {
                case EQUAL -> Arrays.equals(received, expected);
                case CONTAIN -> throw new AssertionError("Not implemented yet!");
            };
        }
    }
}
