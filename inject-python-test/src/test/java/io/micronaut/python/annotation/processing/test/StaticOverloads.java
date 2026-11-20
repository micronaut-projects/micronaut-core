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
package io.micronaut.python.annotation.processing.test;

/**
 * Overloads for the static compilation parity harness: a pair that a call with two ints matches
 * equally well, and pairs that a None or a str argument binds to by its declared type.
 */
@SuppressWarnings("java:S1172") // only the parameter types matter: they make the call ambiguous
public final class StaticOverloads {

    private StaticOverloads() {
    }

    /**
     * @param a A long
     * @param b A double
     * @return Which overload ran
     */
    public static String pick(long a, double b) {
        return "long, double";
    }

    /**
     * @param a A double
     * @param b A long
     * @return Which overload ran
     */
    public static String pick(double a, long b) {
        return "double, long";
    }

    /**
     * @param value A string
     * @return Which overload ran
     */
    public static String which(String value) {
        return "String " + value;
    }

    /**
     * @param value Any value
     * @return Which overload ran
     */
    public static String which(Object value) {
        return "Object " + value;
    }

    /**
     * @param value A character sequence
     * @return Which overload ran
     */
    public static String sequence(CharSequence value) {
        return "CharSequence " + value;
    }

    /**
     * @param value Any value
     * @return Which overload ran
     */
    public static String sequence(Object value) {
        return "Object " + value;
    }
}
