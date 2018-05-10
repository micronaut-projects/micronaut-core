/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.core.util;

import java.util.Objects;

/**
 * Utility methods for checking method argument values.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ArgumentUtils {

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
         * @throws IllegalArgumentException Thrown with the given message if the check fails
         */
        public void notNull() {
            if (name != null && value != null) {
                Objects.requireNonNull(value, "Argument [" + name + "] cannot be null");
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
