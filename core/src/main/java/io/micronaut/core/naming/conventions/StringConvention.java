/*
 * Copyright 2018 original authors
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
package io.micronaut.core.naming.conventions;

import io.micronaut.core.naming.NameUtils;

/**
 * An enum representing different conventions for
 *
 * @author graemerocher
 * @since 1.0
 */
public enum StringConvention {
    /**
     * Camel case capitalized like class names
     *
     * Example: FooBar
     */
    CAMEL_CASE_CAPITALIZED,
    /**
     * Camel case, lower case first letter
     *
     * Example: fooBar
     */
    CAMEL_CASE,

    /**
     * Hyphenated, in lower case.  Example foo-bar
     */
    HYPHENATED,

    /**
     * Hyphenated, in upper case.  Example FOO_BAR
     */
    UNDER_SCORE_SEPARATED;

    /**
     * Format the string with this format
     *
     * @param str The string
     * @return The formatted string
     */
    public String format(String str) {
        return StringConvention.format(this, str);
    }

    public static String format(StringConvention convention, String str) {
        switch (convention) {
            case CAMEL_CASE:
                return NameUtils.camelCase(str);
            case HYPHENATED:
                return NameUtils.hyphenate(str);
            case UNDER_SCORE_SEPARATED:
                return NameUtils.environmentName(str);
            case CAMEL_CASE_CAPITALIZED:
            default:
                return NameUtils.camelCase(str, false);
        }
    }
}
