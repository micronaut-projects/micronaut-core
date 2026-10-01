/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing.util;

import io.micronaut.core.annotation.Internal;

import java.util.Set;

/**
 * Python reserved words and the trailing-underscore alias convention ({@code class_} for {@code class})
 * used wherever a Java name collides with one.
 *
 * @since 5.2.0
 */
@Internal
public final class PythonKeywords {

    private static final Set<String> KEYWORDS = Set.of(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break",
        "class", "continue", "def", "del", "elif", "else", "except", "finally",
        "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
        "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"
    );

    private PythonKeywords() {
    }

    /**
     * @param name A name
     * @return Whether it is a Python keyword
     */
    public static boolean isKeyword(String name) {
        return KEYWORDS.contains(name);
    }

    /**
     * The name a Java identifier gets in Python: keywords receive a trailing underscore.
     *
     * @param name The Java name
     * @return The Python-safe name
     */
    public static String toPythonName(String name) {
        return isKeyword(name) ? name + "_" : name;
    }

    /**
     * The Java identifier behind a Python name: a trailing underscore that shields a keyword is removed.
     *
     * @param name The Python name
     * @return The Java name
     */
    public static String toJavaName(String name) {
        if (name.endsWith("_")) {
            String candidate = name.substring(0, name.length() - 1);
            if (isKeyword(candidate)) {
                return candidate;
            }
        }
        return name;
    }

    /**
     * Applies {@link #toJavaName(String)} to every segment of a dotted name.
     *
     * @param dottedName A package or qualified name in Python form
     * @return The Java form
     */
    public static String toJavaDottedName(String dottedName) {
        String[] parts = dottedName.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = toJavaName(parts[i]);
        }
        return String.join(".", parts);
    }
}
