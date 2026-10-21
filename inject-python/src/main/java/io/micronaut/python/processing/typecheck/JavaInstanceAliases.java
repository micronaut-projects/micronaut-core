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
package io.micronaut.python.processing.typecheck;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.MethodElement;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The Python-facing names of the public instance methods of a Java type: the snake_case alias of a
 * method ({@code basic_auth} for {@code basicAuth}) and the read-only property of a getter
 * ({@code port} for {@code getPort()}, {@code ready} for {@code isReady()}).
 * <p>
 * Computed once by the compiler, for the type checker ({@link TypeFacts.TypeDescription#instanceAliases()})
 * and for the Java imports manifest the runtime registers them from; the runtime never derives a name
 * itself. A name two methods would claim, and the snake_case alias of a method that also has static
 * overloads (its bound member holds both kinds), is {@link Alias#BLOCKED}: blocked rather than left
 * out, so that a supertype cannot alias a name its subtype finds ambiguous.
 *
 * @since 5.3.0
 */
@Internal
public final class JavaInstanceAliases {

    private JavaInstanceAliases() {
    }

    /**
     * @param publicMethods The public methods of the type, inherited ones included
     * @return The aliases by Python name, sorted
     */
    public static Map<String, Alias> of(Collection<MethodElement> publicMethods) {
        Set<String> staticNames = publicMethods.stream().filter(MethodElement::isStatic)
            .map(MethodElement::getName).collect(Collectors.toSet());
        Map<String, Alias> result = new TreeMap<>();
        for (MethodElement method : publicMethods) {
            if (method.isStatic()) {
                continue;
            }
            String name = method.getName();
            String alias = snakeCase(name);
            if (!alias.equals(name)) {
                add(result, alias, staticNames.contains(name) ? Alias.BLOCKED : new Alias(name, false));
            }
            String property = getterProperty(method);
            if (property != null) {
                add(result, property, new Alias(name, true));
            }
        }
        return result;
    }

    private static void add(Map<String, Alias> aliases, String name, Alias alias) {
        aliases.merge(name, alias, (first, second) -> first.equals(second) ? first : Alias.BLOCKED);
    }

    private static @Nullable String getterProperty(MethodElement method) {
        String name = method.getName();
        if (method.getParameters().length != 0 || method.getReturnType().isVoid() || name.equals("getClass")) {
            return null;
        }
        int prefix = getterPrefix(method);
        return prefix > 0 && name.length() > prefix && Character.isUpperCase(name.charAt(prefix))
            ? snakeCase(name.substring(prefix)) : null;
    }

    private static int getterPrefix(MethodElement method) {
        if (method.getName().startsWith("get")) {
            return 3;
        }
        String returnType = method.getReturnType().getName();
        return method.getName().startsWith("is") && (returnType.equals("boolean") || returnType.equals(Boolean.class.getName())) ? 2 : 0;
    }

    private static String snakeCase(String name) {
        // keep an acronym together (HTTPStatus -> http_status) before the camel-case conversion
        return Arrays.stream(name.split("_", -1))
            .map(word -> NameUtils.underscoreSeparate(word.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2"))
                .toLowerCase(Locale.ROOT))
            .collect(Collectors.joining("_"));
    }

    /**
     * A Python name of an instance method.
     *
     * @param target   The Java method the name stands for; empty when the name is {@link #BLOCKED}
     * @param property Whether the name is a read-only property calling the zero-argument getter
     */
    public record Alias(String target, boolean property) {

        /** A name no method of the type may be reached by. */
        public static final Alias BLOCKED = new Alias("", false);

        /**
         * @return Whether the name is ambiguous or excluded
         */
        public boolean blocked() {
            return target.isEmpty();
        }
    }
}
