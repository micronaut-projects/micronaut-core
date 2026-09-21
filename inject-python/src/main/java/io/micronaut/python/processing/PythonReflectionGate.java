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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides which generated Java types carry the reflection data of their Python class: the runtime
 * annotations of third-party annotation types (JPA, JAXB, Jackson, LangChain4j, ...) that a framework
 * reads with {@code Class.getAnnotation} rather than through the Micronaut annotation metadata.
 *
 * <p>Copying such annotations onto the generated source is off by default: a generated class carrying
 * {@code @Entity} or {@code @JsonProperty} is seen by every annotation processor of the following Java
 * processing rounds as if a Java class had declared them, so the copy is made only for the types an
 * application names. The types are named with the property that allows reflective introspection at run
 * time, {@value #PROPERTY} of {@code io.micronaut.reflection.ReflectionIntrospectionPolicy}, and with its
 * pattern language: a comma-separated list of class names where {@code *} stands for any sequence of
 * characters, matched against the whole name. {@code com.example.model.*} names the classes of a package
 * and of its sub packages, {@code com.example.Order} one class, {@code *.Order} every class of that name
 * and {@code *} every class. The compiler reads it from {@link VisitorContext#getOptions()}: as the
 * annotation processor option {@code -A}{@value #OPTION}{@code =...}, the camel-case spelling of the same
 * Micronaut property, since javac only accepts a dot-separated sequence of identifiers as the key of an
 * option, or as the {@value #PROPERTY} system property of the compiler JVM.</p>
 *
 * <p>The gate applies to the reflective copies only. The annotations the test frameworks and Micronaut
 * itself read from the generated class (JUnit, {@code @MicronautTest} and the other {@code @ExtendWith}
 * annotations, the Micronaut annotations declared {@code @ReflectiveAccess}) are copied regardless.</p>
 *
 * @since 5.2.3
 */
@Internal
final class PythonReflectionGate {

    /**
     * The property naming the generated types that carry the reflection data of their Python class:
     * {@code ReflectionIntrospectionPolicy.PROPERTY_ALLOW_REFLECTION}, which the Python compiler cannot
     * reference as the reflection module is a runtime module. Read as a system property of the compiler.
     */
    static final String PROPERTY = "micronaut.introspection.allow-reflection";

    /**
     * The annotation processor option naming the generated types that carry the reflection data of their
     * Python class: the camel-case spelling of {@link #PROPERTY}, the only one javac accepts as the key of a
     * {@code -A} option ({@code allow-reflection} is not an identifier).
     */
    static final String OPTION = "micronaut.introspection.allowReflection";

    /**
     * The annotations whose absence from the generated type is not worth a note: Bean Validation
     * constraints are read by Micronaut Validation from the annotation metadata, so a class that only
     * declares constraints loses nothing without the option.
     */
    private static final List<String> QUIET_ANNOTATION_PACKAGE_PREFIXES = List.of("jakarta.validation.", "javax.validation.");

    private final List<Pattern> patterns;
    private final boolean all;
    private final Set<String> reportedTypes = new HashSet<>();

    private PythonReflectionGate(List<Pattern> patterns) {
        this.patterns = patterns;
        this.all = patterns == null;
    }

    /**
     * The gate configured by the options of a compilation.
     *
     * @param visitorContext The visitor context
     * @return The gate
     */
    @NonNull
    static PythonReflectionGate of(@NonNull VisitorContext visitorContext) {
        Map<String, String> options = visitorContext.getOptions();
        List<String> patterns = new ArrayList<>();
        for (String name : List.of(OPTION, PROPERTY)) {
            String value = options.get(name);
            if (StringUtils.isNotEmpty(value)) {
                patterns.addAll(List.of(value.split(",")));
            }
        }
        return of(patterns);
    }

    /**
     * The gate of a comma-separated list of patterns.
     *
     * @param patterns The patterns, {@code null} or blank for none
     * @return The gate
     */
    @NonNull
    static PythonReflectionGate of(@Nullable String patterns) {
        if (StringUtils.isEmpty(patterns)) {
            return new PythonReflectionGate(List.of());
        }
        return of(List.of(patterns.split(",")));
    }

    /**
     * The gate of the given patterns.
     *
     * @param patterns The patterns, {@code *} standing for any sequence of characters
     * @return The gate
     */
    @NonNull
    static PythonReflectionGate of(@NonNull Collection<String> patterns) {
        List<Pattern> compiled = new ArrayList<>(patterns.size());
        for (String pattern : patterns) {
            String trimmed = pattern.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if ("*".equals(trimmed) || "**".equals(trimmed)) {
                return new PythonReflectionGate(null);
            }
            // the literals between the stars are quoted, so a dot of a package name is a dot and not any
            // character, and a star becomes the only wildcard there is; the regex is matched against the
            // whole name, which is why a leading star has to be written out as well as one in the middle
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
        return new PythonReflectionGate(List.copyOf(compiled));
    }

    /**
     * Whether no type is allowed.
     *
     * @return Whether the gate is closed for every type
     */
    boolean isEmpty() {
        return !all && patterns.isEmpty();
    }

    /**
     * Whether a generated type carries the reflection data of its Python class.
     *
     * @param typeName The fully qualified name of the generated type
     * @return Whether the type matches one of the patterns
     */
    boolean allows(@NonNull String typeName) {
        if (all) {
            return true;
        }
        for (Pattern pattern : patterns) {
            if (pattern.matcher(typeName).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a generated type carries the reflection data of its Python class, telling the user once
     * per type when it does not: the annotation a framework expects to find reflectively is left off the
     * generated source, and the option that puts it there is named so that the omission can be found.
     * Bean Validation constraints, which Micronaut reads from the annotation metadata, are left off
     * without a note.
     *
     * @param typeName       The fully qualified name of the generated type
     * @param annotationName The annotation that is about to be left off
     * @param element        The annotated Python element
     * @param visitorContext The visitor context
     * @return Whether the type matches one of the patterns
     */
    boolean allows(@NonNull String typeName, @NonNull String annotationName, @NonNull Element element, @NonNull VisitorContext visitorContext) {
        if (allows(typeName)) {
            return true;
        }
        if (isReported(annotationName) && reportedTypes.add(typeName)) {
            visitorContext.info("The runtime annotations of [" + typeName + "] (@" + annotationName + ", ...) are not copied onto the generated"
                + " Java type, so frameworks reading them reflectively will not see them; name the type in the "
                + OPTION + " annotation processor option (-A" + OPTION + "=" + typeName + ") to copy them", element);
        }
        return false;
    }

    private static boolean isReported(String annotationName) {
        for (String prefix : QUIET_ANNOTATION_PACKAGE_PREFIXES) {
            if (annotationName.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    /**
     * How an annotation of a Python element is copied onto the generated Java declaration.
     */
    enum Copy {
        /**
         * Never: the annotation is served by the annotation metadata, has no runtime retention or cannot
         * be placed on the declaration.
         */
        NEVER,
        /**
         * Always: the test framework or Micronaut itself reads it from the generated type.
         */
        ALWAYS,
        /**
         * Only when the gate allows the generated type: reflection data of a third-party framework.
         */
        REFLECTIVE
    }
}
