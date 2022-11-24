/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.util;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.Described;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;

/**
 * Utility methods for checking method argument values.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ArgumentUtils {

    private static final String MSG_PREFIX_ARGUMENT = "Argument [";

    /**
     * Adds a check that the given number is positive.
     *
     * @param name The name of the argument
     * @param value The value
     * @throws IllegalArgumentException if the argument is not positive
     * @return The value
     */
    public static @NonNull Number requirePositive(String name, Number value) {
        requireNonNull(name, value);
        requirePositive(name, value.intValue());
        return value;
    }

    /**
     * Adds a check that the given number is not null.
     *
     * @param name The name of the argument
     * @param value The value
     * @param <T> The generic type
     * @throws NullPointerException if the argument is null
     * @return The value
     */
    public static <T> T requireNonNull(String name, T value) {
        if (value == null) {
            throw new NullPointerException(MSG_PREFIX_ARGUMENT + name + "] cannot be null");
        }
        return value;
    }

    /**
     * Adds a check that the given number is positive.
     *
     * @param name The name of the argument
     * @param value The value
     * @throws IllegalArgumentException if the argument is not positive
     * @return The value
     */
    public static int requirePositive(String name, int value) {
        if (value < 0) {
            throw new IllegalArgumentException(MSG_PREFIX_ARGUMENT + name + "] cannot be negative");
        }
        return value;
    }

    /**
     * Perform a check on an argument.
     *
     * @param check The check
     * @return The {@link ArgumentCheck}
     */
    public static ArgumentCheck check(Check check) {
        return new ArgumentCheck(check);
    }

    /**
     * Perform a check on an argument.
     *
     * @param name  The name of the argument
     * @param value The value of the argument
     * @param <T>   The value type
     * @return The {@link ArgumentCheck}
     */
    public static <T> ArgumentCheck check(String name, T value) {
        return new ArgumentCheck<>(name, value);
    }

    /**
     * Validates the given values are appropriate for the given arguments.
     * @param described The described instance
     * @param arguments The arguments
     * @param values The values
     */
    public static void validateArguments(
            @NonNull Described described,
            @NonNull Argument<?>[] arguments,
            @NonNull Object[] values) {
        int requiredCount = arguments.length;
        @SuppressWarnings("ConstantConditions") int actualCount = ArrayUtils.isEmpty(values) ? 0 : values.length;
        if (requiredCount != actualCount) {
            throw new IllegalArgumentException("Wrong number of arguments to " + (described instanceof Executable ? "method" : "constructor") + ": " + described.getDescription());
        }
        if (requiredCount > 0) {
            for (int i = 0; i < arguments.length; i++) {
                Argument<?> argument = arguments[i];
                Class<?> type = argument.getWrapperType();
                Object value = values[i];
                if (value != null && !type.isInstance(value)) {
                    throw new IllegalArgumentException("Invalid type [" + values[i].getClass().getName() + "] for argument [" + argument + "] of " + (described instanceof Executable ? "method" : "constructor") + ": " + described.getDescription());
                }
            }
        }
    }

    /**
     * Allows producing error messages.
     *
     * @param <T> The type
     */
    public static class ArgumentCheck<T> {
        private final Check check;
        private final String name;
        private final T value;

        /**
         * @param check The check
         */
        public ArgumentCheck(Check check) {
            this.check = check;
            this.name = null;
            this.value = null;
        }

        /**
         * @param name  The name
         * @param value The value
         */
        public ArgumentCheck(String name, T value) {
            this.check = null;
            this.name = name;
            this.value = value;
        }

        /**
         * Fail the argument with the given message.
         *
         * @param message The message
         * @throws IllegalArgumentException Thrown with the given message if the check fails
         */
        public void orElseFail(String message) {
            if (check != null && !check.condition()) {
                throw new IllegalArgumentException(message);
            }
        }

        /**
         * Fail the argument with the given message.
         *
         * @throws NullPointerException Thrown with the given message if the check fails
         */
        public void notNull() {
            if (value == null) {
                throw new NullPointerException(MSG_PREFIX_ARGUMENT + name + "] cannot be null");
            }
        }
    }

    /**
     * Functional interface the check a condition.
     */
    @FunctionalInterface
    public interface Check {

        /**
         * @return Whether the condition is true
         */
        boolean condition();
    }
}
