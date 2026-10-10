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
package io.micronaut.python.processing;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.python.processing.model.ClassDef;
import io.micronaut.python.processing.model.TypeRef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides which Python classes of the sources get no Java type: they stay in the Python sources of the
 * application and run as they are, but the compiler models them as it does a class of a module outside
 * the sources, so a type hint naming one is an {@code Object} to Java and nothing is generated for them.
 *
 * <p>Two kinds of class are left out (micronaut-projects/pyronaut#331):</p>
 * <ul>
 *     <li>The classes named with {@code -A}{@value #OPTION}{@code =...}, a comma-separated list of qualified
 *     class names where {@code *} stands for any sequence of characters, matched against the whole name:
 *     {@code com.example.internal.*} names the classes of a package and of its sub packages,
 *     {@code com.example.Helper} one class. The option is also read as a system property of the compiler JVM.</li>
 *     <li>A private helper: a top level class whose name starts with {@code _}, that has no decorator and
 *     whose bases are other such classes or Python's own {@code object}, {@code ABC} and {@code Generic}.
 *     Nothing in Java can name such a class, and with no decorator and no Java base there is nothing about
 *     it for Micronaut to process.</li>
 * </ul>
 *
 * <p>A class another kept class extends is kept, so the Java type of the subclass keeps its base.</p>
 */
@Internal
final class PythonClassExclusions {

    /**
     * The option naming the Python classes to generate no Java type for.
     */
    static final String OPTION = "micronaut.python.exclude";

    private static final Set<String> NEUTRAL_BASES = Set.of(
        "object", "ABC", "abc.ABC", "Generic", "typing.Generic"
    );

    private PythonClassExclusions() {
    }

    /**
     * The classes of the environment that get a Java type.
     *
     * @param classes The parsed classes by qualified name
     * @param option  The value of {@value #OPTION}, or {@code null}
     * @return The kept classes by qualified name
     */
    static Map<String, ClassDef> retain(Map<String, ClassDef> classes, @Nullable String option) {
        List<Pattern> patterns = patterns(StringUtils.isEmpty(option) ? System.getProperty(OPTION) : option);
        Set<String> excluded = new HashSet<>();
        for (Map.Entry<String, ClassDef> entry : classes.entrySet()) {
            if (matches(patterns, entry.getKey())) {
                excluded.add(entry.getKey());
            }
        }
        Set<String> privateHelpers = new HashSet<>();
        for (Map.Entry<String, ClassDef> entry : classes.entrySet()) {
            if (isPrivateHelperCandidate(entry.getValue())) {
                privateHelpers.add(entry.getKey());
            }
        }
        // a private helper extending a class that is kept is not a private helper after all
        boolean changed = true;
        while (changed) {
            changed = privateHelpers.removeIf(name -> classes.get(name).bases().stream()
                .anyMatch(base -> !NEUTRAL_BASES.contains(base.name())
                    && !privateHelpers.contains(resolve(classes, classes.get(name), base))));
        }
        excluded.addAll(privateHelpers);
        // the nested classes of a class left out go with it
        Set<String> enclosing = Set.copyOf(excluded);
        for (String name : classes.keySet()) {
            if (enclosing.stream().anyMatch(outer -> name.startsWith(outer + '.'))) {
                excluded.add(name);
            }
        }
        // a class a kept class extends is kept with it
        changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, ClassDef> entry : classes.entrySet()) {
                if (excluded.contains(entry.getKey())) {
                    continue;
                }
                for (TypeRef base : entry.getValue().bases()) {
                    String baseName = resolve(classes, entry.getValue(), base);
                    if (baseName != null && excluded.remove(baseName)) {
                        changed = true;
                    }
                }
            }
        }
        if (excluded.isEmpty()) {
            return classes;
        }
        Map<String, ClassDef> retained = new LinkedHashMap<>(classes);
        retained.keySet().removeAll(excluded);
        return retained;
    }

    private static boolean isPrivateHelperCandidate(ClassDef classDef) {
        String name = classDef.name();
        return name.startsWith("_")
            && name.indexOf('.') == -1
            && classDef.decorators().isEmpty()
            && !classDef.isEnum()
            && classDef.bases().stream().noneMatch(TypeRef::nativeException);
    }

    /**
     * The qualified name of the class of the sources a base refers to, or {@code null} for a class
     * of a module outside the sources or a Java type.
     */
    private static @Nullable String resolve(Map<String, ClassDef> classes, ClassDef classDef, TypeRef base) {
        String name = base.name();
        if (name == null) {
            return null;
        }
        if (classes.containsKey(name)) {
            return name;
        }
        String qualified = classDef.packageName() + '.' + name;
        return classes.containsKey(qualified) ? qualified : null;
    }

    private static boolean matches(List<Pattern> patterns, String name) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    private static List<Pattern> patterns(@Nullable String value) {
        if (StringUtils.isEmpty(value)) {
            return List.of();
        }
        List<Pattern> compiled = new ArrayList<>();
        for (String pattern : value.split(",")) {
            String trimmed = pattern.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // as in PythonReflectionGate: the literals between the stars are quoted, so a dot is a dot
            String[] literals = trimmed.split("\\*", -1);
            StringBuilder regex = new StringBuilder(trimmed.length() + 8);
            for (int i = 0; i < literals.length; i++) {
                if (i > 0) {
                    regex.append(".*");
                }
                if (!literals[i].isEmpty()) {
                    regex.append(Pattern.quote(literals[i]));
                }
            }
            compiled.add(Pattern.compile(regex.toString()));
        }
        return compiled;
    }
}
