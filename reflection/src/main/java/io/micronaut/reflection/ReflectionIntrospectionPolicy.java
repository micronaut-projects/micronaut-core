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
package io.micronaut.reflection;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The types the shared {@link io.micronaut.core.beans.BeanIntrospector} may describe reflectively when it has
 * no generated introspection for them.
 *
 * <p>Reading a class back is what the framework is built to avoid, and a reflective introspection exposes every
 * member of a class to whatever asks for it, so no type is allowed until an application says so. A type is
 * allowed when its name matches one of the patterns of the {@value #PROPERTY_ALLOW_REFLECTION} property, set
 * either as a system property or in the application configuration, or one of the patterns
 * {@linkplain #allow(String...) allowed} programmatically. A pattern is a class name where {@code *} stands for
 * any sequence of characters: {@code com.example.model.*} allows a package and its sub packages,
 * {@code com.example.Order} one class and {@code *} every class.</p>
 *
 * <p>The policy guards only the fallback of the shared introspector. Code that reflects on a type explicitly -
 * {@link ReflectionBeanIntrospection#of(Class)}, {@link ReflectionBeanIntrospector} - is not subject to it: that
 * code made its choice.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public final class ReflectionIntrospectionPolicy {

    /**
     * The property listing the patterns of the types the shared introspector may describe reflectively.
     */
    public static final String PROPERTY_ALLOW_REFLECTION = "micronaut.introspection.allow-reflection";

    private static final Predicate<Class<?>> NONE = type -> false;
    private static final Predicate<Class<?>> SYSTEM = patterns(split(System.getProperty(PROPERTY_ALLOW_REFLECTION)));
    private static volatile Predicate<Class<?>> configured = NONE;
    private static volatile Predicate<Class<?>> allowed = NONE;

    private ReflectionIntrospectionPolicy() {
    }

    /**
     * Whether a type is allowed.
     *
     * @param type The type
     * @return Whether the shared introspector may describe the type reflectively
     */
    public static boolean isAllowed(Class<?> type) {
        return SYSTEM.test(type) || configured.test(type) || allowed.test(type);
    }

    /**
     * Allows the types matching the patterns, in addition to the ones already allowed.
     *
     * @param patterns The patterns
     */
    public static void allow(String... patterns) {
        allow(List.of(patterns));
    }

    /**
     * Allows the types matching the patterns, in addition to the ones already allowed.
     *
     * @param patterns The patterns
     */
    public static void allow(Collection<String> patterns) {
        allow(patterns(patterns));
    }

    /**
     * Allows the types a predicate accepts, in addition to the ones already allowed.
     *
     * @param types The predicate
     */
    public static void allow(Predicate<Class<?>> types) {
        Predicate<Class<?>> current = allowed;
        allowed = current == NONE ? types : current.or(types);
    }

    /**
     * Sets the patterns the application configuration allows, replacing the ones a previous configuration
     * allowed. The system property and the programmatically allowed types are not affected.
     *
     * @param patterns The patterns
     */
    public static void configure(Collection<String> patterns) {
        configured = patterns(patterns);
    }

    /**
     * Forgets the configured and the programmatically allowed types. The system property still applies.
     */
    public static void reset() {
        configured = NONE;
        allowed = NONE;
    }

    /**
     * The predicate matching the class names of the given patterns.
     *
     * @param patterns The patterns, {@code *} standing for any sequence of characters
     * @return The predicate
     */
    static Predicate<Class<?>> patterns(Collection<String> patterns) {
        List<Pattern> compiled = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            String trimmed = pattern.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if ("*".equals(trimmed) || "**".equals(trimmed)) {
                return type -> true;
            }
            StringBuilder regex = new StringBuilder();
            for (String literal : trimmed.split("\\*", -1)) {
                if (!regex.isEmpty()) {
                    regex.append(".*");
                }
                if (!literal.isEmpty()) {
                    regex.append(Pattern.quote(literal));
                }
            }
            compiled.add(Pattern.compile(regex.toString()));
        }
        if (compiled.isEmpty()) {
            return NONE;
        }
        List<Pattern> matchers = List.copyOf(compiled);
        return type -> {
            String name = type.getName();
            for (Pattern matcher : matchers) {
                if (matcher.matcher(name).matches()) {
                    return true;
                }
            }
            return false;
        };
    }

    private static List<String> split(String value) {
        if (StringUtils.isEmpty(value)) {
            return List.of();
        }
        return List.of(value.split(","));
    }
}
