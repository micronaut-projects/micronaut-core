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

import io.micronaut.annotation.processing.visitor.JavaMethodElement;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.inject.ast.MethodElement;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

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

    private static final Pattern ACRONYM_BOUNDARY = Pattern.compile("([A-Z]+)([A-Z][a-z])");

    private JavaInstanceAliases() {
    }

    /**
     * @param publicMethods The public methods of the type, inherited ones included
     * @return The aliases by Python name, sorted
     */
    public static Map<String, Alias> of(Collection<MethodElement> publicMethods) {
        Set<String> staticNames = null;
        for (MethodElement method : publicMethods) {
            if (method.isStatic()) {
                if (staticNames == null) {
                    staticNames = new HashSet<>();
                }
                staticNames.add(method.getName());
            }
        }
        Map<String, Alias> result = new TreeMap<>();
        for (MethodElement method : publicMethods) {
            if (method.isStatic()) {
                continue;
            }
            String name = method.getName();
            String alias = snakeCase(name);
            if (!alias.equals(name)) {
                add(result, alias, staticNames != null && staticNames.contains(name) ? Alias.BLOCKED : new Alias(name, false));
            }
            String property = getterProperty(method, name);
            if (property != null) {
                add(result, property, new Alias(name, true));
            }
        }
        return result;
    }

    private static void add(Map<String, Alias> aliases, String name, Alias alias) {
        aliases.merge(name, alias, (first, second) -> first.equals(second) ? first : Alias.BLOCKED);
    }

    private static @Nullable String getterProperty(MethodElement method, String name) {
        int prefix;
        if (name.startsWith("get") && name.length() > 3 && Character.isUpperCase(name.charAt(3)) && !name.equals("getClass")) {
            prefix = 3;
        } else if (name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2))) {
            prefix = 2;
        } else {
            return null;
        }
        if (method.getParameters().length != 0) {
            return null;
        }
        if (prefix == 2) {
            if (!isBooleanReturn(method)) {
                return null;
            }
        } else if (isVoidReturn(method)) {
            return null;
        }
        return snakeCase(name.substring(prefix));
    }

    private static boolean isVoidReturn(MethodElement method) {
        if (method instanceof JavaMethodElement javaMethod) {
            return javaMethod.getNativeType().element().getReturnType().getKind() == TypeKind.VOID;
        }
        return method.getReturnType().isVoid();
    }

    private static boolean isBooleanReturn(MethodElement method) {
        if (method instanceof JavaMethodElement javaMethod) {
            TypeMirror returnType = javaMethod.getNativeType().element().getReturnType();
            if (returnType.getKind() == TypeKind.BOOLEAN) {
                return true;
            }
            if (returnType instanceof DeclaredType declaredType && declaredType.asElement() instanceof TypeElement te) {
                return Boolean.class.getName().equals(te.getQualifiedName().toString());
            }
            return false;
        }
        String returnType = method.getReturnType().getName();
        return returnType.equals("boolean") || returnType.equals(Boolean.class.getName());
    }

    private static String snakeCase(String name) {
        if (name.indexOf('_') < 0) {
            return NameUtils.underscoreSeparate(ACRONYM_BOUNDARY.matcher(name).replaceAll("$1_$2"))
                .toLowerCase(Locale.ROOT);
        }
        // keep an acronym together (HTTPStatus -> http_status) before the camel-case conversion
        String[] parts = name.split("_", -1);
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('_');
            }
            sb.append(NameUtils.underscoreSeparate(ACRONYM_BOUNDARY.matcher(parts[i]).replaceAll("$1_$2"))
                .toLowerCase(Locale.ROOT));
        }
        return sb.toString();
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
        public boolean isBlocked() {
            return target.isEmpty();
        }
    }
}
