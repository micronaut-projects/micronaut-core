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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationBuilderRegistry;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.RegisterAnnotations;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The annotation builders written in one compilation, by {@link RegisterAnnotations} and by
 * {@link VisitorContext#registerAnnotationBuilder(ClassElement, Element)}.
 *
 * <p>A compilation is identified by an object of the language processor that every visitor context of the
 * compilation shares, such as the element utilities of javac or the compilation unit of Groovy, so that the
 * processors of a compilation, and the visitor contexts of its rounds and source units, do not write a builder
 * twice.</p>
 *
 * <p>How far a request is shared depends on the kind of the visitor, so that the builders stay right in an
 * incremental compilation, which processes the changed types only:</p>
 * <ul>
 *     <li>an {@link TypeElementVisitor.VisitorKind#AGGREGATING aggregating} visitor processes all of its types in
 *     every compilation, so its requests share one builder per annotation type in the compilation;</li>
 *     <li>an {@link TypeElementVisitor.VisitorKind#ISOLATING isolating} visitor processes the changed types only,
 *     and a builder belongs to the type of its originating element, so each type gets its own builder, which is
 *     written again whenever the type is.</li>
 * </ul>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class AnnotationBuilderRequests {

    private static final Map<Object, AnnotationBuilderRequests> COMPILATIONS = Collections.synchronizedMap(new WeakHashMap<>());

    private static final String PACKAGE_HOLDER = ".package-info";

    /**
     * The names of the annotation types with a builder requested by an aggregating visitor.
     */
    private final Set<String> aggregated = new HashSet<>();
    /**
     * The names of the written builders.
     */
    private final Set<String> builders = new HashSet<>();

    private AnnotationBuilderRequests() {
    }

    /**
     * The builders of a compilation.
     *
     * @param compilation The object identifying the compilation, held weakly
     * @return The builders written in the compilation
     */
    public static AnnotationBuilderRequests forCompilation(Object compilation) {
        return COMPILATIONS.computeIfAbsent(compilation, key -> new AnnotationBuilderRequests());
    }

    /**
     * Writes a builder for an annotation type, unless the compilation already has one that the request can share.
     *
     * @param annotationType     The annotation type
     * @param originatingElement The originating element, the builder is placed next to its type or in its package
     * @param visitorKind        The kind of the visitor that requests the builder
     * @param context            The visitor context
     * @return {@code true} when the compilation has a builder for the type, {@code false} when it cannot have one
     * because the type is not public
     * @see VisitorContext#registerAnnotationBuilder(ClassElement, Element)
     */
    public synchronized boolean register(ClassElement annotationType,
                                         Element originatingElement,
                                         TypeElementVisitor.VisitorKind visitorKind,
                                         VisitorContext context) {
        if (!annotationType.isPublic()) {
            return false;
        }
        if (visitorKind == TypeElementVisitor.VisitorKind.AGGREGATING) {
            if (aggregated.add(annotationType.getName())) {
                write(holderName(originatingElement), annotationType, originatingElement, context);
            }
        } else {
            write(holderName(originatingElement), annotationType, originatingElement, context);
        }
        return true;
    }

    /**
     * Writes the builder of an annotation type listed in {@link RegisterAnnotations}, next to the type or package
     * that lists it, unless the same builder was already written in the compilation.
     *
     * @param holderName     The binary name of the type, or the package followed by {@code .package-info}
     * @param annotationType The annotation type
     * @param origin         The type or package that lists the annotation type
     * @param context        The visitor context
     */
    synchronized void registerListed(String holderName, ClassElement annotationType, Element origin, VisitorContext context) {
        write(holderName, annotationType, origin, context);
    }

    private void write(String holderName, ClassElement annotationType, Element origin, VisitorContext context) {
        String annotationName = annotationType.getName();
        String builderName = holderName + "$" + AnnotationBuilderRegistry.mangle(annotationName) + AnnotationBuilderRegistry.BUILDER_SUFFIX;
        if (!builders.add(builderName)) {
            return;
        }
        try {
            AnnotationBuilderWriter.write(holderName, annotationType, origin, context);
        } catch (IOException e) {
            throw new ProcessingException(origin, "Failed to write the annotation builder of " + annotationName + ": " + e.getMessage(), e);
        }
    }

    private static String holderName(Element originatingElement) {
        if (originatingElement instanceof ClassElement classElement) {
            return classElement.getName();
        }
        if (originatingElement instanceof MemberElement memberElement) {
            return memberElement.getOwningType().getName();
        }
        if (originatingElement instanceof PackageElement packageElement) {
            return packageElement.getName() + PACKAGE_HOLDER;
        }
        throw new IllegalArgumentException("The originating element of an annotation builder has to be a type, a member of a type or a package: " + originatingElement);
    }
}
