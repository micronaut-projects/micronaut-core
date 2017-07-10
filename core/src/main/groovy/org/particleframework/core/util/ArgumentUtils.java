/*
 * Copyright 2017 original authors
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
package org.particleframework.core.util;

/**
 * Utility methods for checking method argument values
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ArgumentUtils {


    /**
     * Perform a check on an argument
     *
     * @param check The check
     * @return The {@link ArgumentCheck}
     */
    public static ArgumentCheck check(Check check) {
        return new ArgumentCheck(check);
    }

    /**
     * Allows producing error messages
     */
    public static class ArgumentCheck {
        private final Check check;

        public ArgumentCheck(Check check) {
            this.check = check;
        }

        /**
         * Fail the argument with the given message
         *
         * @param message The message
         * @throws  IllegalArgumentException Thrown with the given message if the check fails
         */
        public void orElseFail(String message) {
            if(!check.condition()) {
                throw new IllegalArgumentException(message);
            }
        }
    }

    @FunctionalInterface
    public interface Check {
        boolean condition();
    }
}
