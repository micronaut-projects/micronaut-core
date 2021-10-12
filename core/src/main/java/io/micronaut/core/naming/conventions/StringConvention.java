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
package io.micronaut.core.naming.conventions;

import io.micronaut.core.naming.NameUtils;

import java.util.Locale;

/**
 * An enum representing different conventions.
 *
 * @author graemerocher
 * @since 1.0
 */
public enum StringConvention {

    /**
     * Camel case capitalized like class names.
     * Example: FooBar
     */
    CAMEL_CASE_CAPITALIZED,

    /**
     * Camel case, lower case first letter.
     * Example: fooBar
     */
    CAMEL_CASE,

    /**
     * Hyphenated, in lower case.
     * Example foo-bar
     */
    HYPHENATED,

    /**
     * Raw unconverted string.
     */
    RAW,

    /**
     * Hyphenated, in upper case.
     * Example FOO_BAR
     */
    UNDER_SCORE_SEPARATED,

    /**
     * Hyphenated, in lower case.
     * Example foo_bar
     */
    UNDER_SCORE_SEPARATED_LOWER_CASE;

    /**
     * Format the string with this format.
     *
     * @param str The string
     * @return The formatted string
     */
    public String format(String str) {
        return StringConvention.format(this, str);
    }

    /**
     * Format a string according to a convention.
     *
     * @param convention The string convention to use
     * @param str        The string to format
     * @return The formatted string based on the convention
     */
    public static String format(StringConvention convention, String str) {
        switch (convention) {
            case CAMEL_CASE:
                return NameUtils.camelCase(str);
            case HYPHENATED:
                return NameUtils.hyphenate(str);
            case UNDER_SCORE_SEPARATED_LOWER_CASE:
                return NameUtils.underscoreSeparate(str.toLowerCase(Locale.ENGLISH));
            case UNDER_SCORE_SEPARATED:
                return NameUtils.environmentName(str);
            case CAMEL_CASE_CAPITALIZED:
                return NameUtils.camelCase(str, false);
            case RAW:
            default:
                return str;
        }
    }
}
