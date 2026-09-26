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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.ast.AnnotationElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.EnumElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.util.PythonJavaTypes;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What the type checker knows about the Java types a compilation uses, answered from the visitor
 * context and cached for the compilation: the Python side asks once per type and works on plain
 * descriptions, so each type costs one lookup however often it is used.
 *
 * @since 5.3.0
 */
@Experimental
public final class TypeFacts {

    private final VisitorContext visitorContext;
    private final Map<String, Optional<AnnotationDescription>> annotations = new HashMap<>();

    /**
     * @param visitorContext The context resolving the Java and Python classes of the compilation
     */
    public TypeFacts(VisitorContext visitorContext) {
        this.visitorContext = Objects.requireNonNull(visitorContext, "visitorContext");
    }

    /**
     * Describes the annotation a decorator resolves to.
     *
     * @param qualifiedName The qualified name the decorator resolved to
     * @return The description, or {@code null} when the name is not a class of the compilation
     * or its classpath at all (a plain Python decorator)
     */
    public @Nullable AnnotationDescription describeAnnotation(String qualifiedName) {
        return annotations.computeIfAbsent(qualifiedName, name -> Optional.ofNullable(loadAnnotation(name))).orElse(null);
    }

    private @Nullable AnnotationDescription loadAnnotation(String qualifiedName) {
        ClassElement element = resolveClass(qualifiedName);
        if (element == null) {
            return null;
        }
        boolean pythonDefined = PythonJavaTypes.isPythonClass(element);
        boolean annotation = element instanceof AnnotationElement || element.isAssignable(Annotation.class);
        if (!annotation) {
            return new AnnotationDescription(element.getName(), false, pythonDefined, false, Map.of(), List.of());
        }
        // an around or introduction binding applied to a class advises every method of the class,
        // whatever the annotation's own targets say
        boolean interceptorBinding = element.hasStereotype("io.micronaut.aop.InterceptorBinding")
            || element.hasStereotype("io.micronaut.aop.Around")
            || element.hasStereotype("io.micronaut.aop.Introduction");
        Map<String, MemberDescription> members = new LinkedHashMap<>();
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared().onlyInstance())) {
            ClassElement returnType = method.getReturnType();
            boolean array = returnType.isArray();
            ClassElement componentType = array ? returnType.fromArray() : returnType;
            List<String> enumConstants = componentType instanceof EnumElement enumElement ? List.copyOf(enumElement.values()) : List.of();
            members.put(method.getName(), new MemberDescription(
                method.getName(),
                componentType.getName(),
                array,
                componentType.isEnum(),
                enumConstants
            ));
        }
        List<String> targets = element instanceof AnnotationElement annotationElement
            ? annotationElement.getTargets().stream().map(ElementType::name).sorted().toList()
            : List.of();
        return new AnnotationDescription(element.getName(), true, pythonDefined, interceptorBinding, members, targets);
    }

    /**
     * Looks a class up by its qualified name, trying the nested-class spellings
     * ({@code a.b.Outer$Nested}) when the dotted name is not a class.
     */
    private @Nullable ClassElement resolveClass(String qualifiedName) {
        String candidate = qualifiedName;
        while (true) {
            Optional<ClassElement> element = visitorContext.getClassElement(candidate);
            if (element.isPresent()) {
                return element.get();
            }
            int lastDot = candidate.lastIndexOf('.');
            if (lastDot <= 0) {
                return null;
            }
            candidate = candidate.substring(0, lastDot) + '$' + candidate.substring(lastDot + 1);
        }
    }

    /**
     * An annotation type as the checker sees it.
     *
     * @param name               The qualified name of the type
     * @param annotation         Whether the type is an annotation type at all
     * @param pythonDefined      Whether the type is generated from a Python definition
     * @param interceptorBinding Whether the annotation binds around or introduction advice, which a
     *                           class applies to all of its methods
     * @param members            The members by name
     * @param targets            The names of the {@link ElementType}s the annotation may be applied to;
     *                           empty when unknown
     */
    public record AnnotationDescription(String name,
                                        boolean annotation,
                                        boolean pythonDefined,
                                        boolean interceptorBinding,
                                        Map<String, MemberDescription> members,
                                        List<String> targets) {

        public AnnotationDescription {
            // the declaration order of the members is the order of a Python-defined annotation's parameters
            members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
            targets = List.copyOf(targets);
        }

        /**
         * @return The simple name, nested types separated by dots
         */
        public String simpleName() {
            return name.substring(name.lastIndexOf('.') + 1).replace('$', '.');
        }
    }

    /**
     * A member of an annotation type.
     *
     * @param name          The member name
     * @param type          The qualified name of the member's type, or of its component type for an array member
     * @param array         Whether the member is an array
     * @param enumType      Whether the member's (component) type is an enum
     * @param enumConstants The constants of that enum, when known
     */
    public record MemberDescription(String name, String type, boolean array, boolean enumType, List<String> enumConstants) {

        public MemberDescription {
            enumConstants = List.copyOf(enumConstants);
        }

        /**
         * @return The type as Java spells it
         */
        public String typeName() {
            return array ? type + "[]" : type;
        }
    }
}
