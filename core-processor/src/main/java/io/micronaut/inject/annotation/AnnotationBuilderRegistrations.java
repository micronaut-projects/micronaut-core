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
import io.micronaut.inject.ast.AnnotationElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.VisitorContext;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Registers the annotation builders of a compilation: the ones {@link RegisterAnnotations} lists and the ones the
 * visitors request with {@link VisitorContext#registerAnnotationBuilder(ClassElement, Element...)}.
 *
 * <p>A builder is written next to its owner, the type or the package of the first originating element, and is
 * written once per owner and annotation type in the compilation, however many times it is registered. The builder
 * of an annotation type needs the builders of the annotation types of its members, which are registered with it,
 * for the same owner. The builders are written when they are registered, so that no element of the compiler is
 * kept after its round; only the names of the written builders, and of the annotation types they were written for,
 * are kept.</p>
 *
 * <p>A compilation is identified by an object of the compiler that every visitor context of the compilation
 * shares, such as the element utilities of javac or the compilation unit of Groovy, so that the processors of a
 * compilation and the visitor contexts of its rounds and source units do not write a builder twice.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class AnnotationBuilderRegistrations {

    private static final Map<Object, AnnotationBuilderRegistrations> COMPILATIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final String PACKAGE_HOLDER = ".package-info";
    private static final Set<String> ALREADY_WRITTEN = Set.of("javax.annotation.processing.FilerException", "kotlin.io.FileAlreadyExistsException");

    /**
     * The names of the annotation types by the names of their written builders.
     */
    private final Map<String, String> builders = new HashMap<>();

    private AnnotationBuilderRegistrations() {
    }

    /**
     * The builders of a compilation.
     *
     * @param compilation The object identifying the compilation, held weakly
     * @return The builders registered in the compilation
     */
    public static AnnotationBuilderRegistrations forCompilation(Object compilation) {
        return COMPILATIONS.computeIfAbsent(compilation, key -> new AnnotationBuilderRegistrations());
    }

    /**
     * Registers the builder of an annotation type, and of the annotation types of its members.
     *
     * @param annotationType      The annotation type
     * @param context             The visitor context
     * @param originatingElements The originating elements, at least one; the first is a type, a member of a type or
     *                            a package, and the builder is written next to its type or into its package
     * @throws IllegalArgumentException When no originating element is given, or the first one is of another kind
     * @throws ProcessingException      When the type is not an annotation, or an annotation type or an enum type
     *                                  of the builders is not accessible from another package
     * @see VisitorContext#registerAnnotationBuilder(ClassElement, Element...)
     */
    public void register(ClassElement annotationType, VisitorContext context, Element... originatingElements) {
        if (originatingElements.length == 0) {
            throw new IllegalArgumentException("The builder of the annotation [" + annotationType.getName() + "] needs an originating element");
        }
        Element origin = originatingElements[0];
        String holderName = holderName(origin);
        Map<String, ClassElement> closure = new LinkedHashMap<>();
        collect(resolve(annotationType, context), origin, context, closure);
        synchronized (this) {
            for (ClassElement type : closure.values()) {
                write(holderName, type, context, originatingElements);
            }
        }
    }

    /**
     * The annotation type and the annotation types of its members, each checked to be accessible from another
     * package, as the builders reference them from the package of the owner.
     */
    private static void collect(ClassElement annotationType, Element origin, VisitorContext context, Map<String, ClassElement> closure) {
        String name = annotationType.getName();
        if (closure.containsKey(name)) {
            return;
        }
        if (!(annotationType instanceof AnnotationElement)) {
            throw new ProcessingException(origin, "Only an annotation type can have an annotation builder: " + name);
        }
        requireAccessible(annotationType, name, origin);
        closure.put(name, annotationType);
        for (AnnotationBuilderWriter.Member member : AnnotationBuilderWriter.membersOf(annotationType)) {
            ClassElement component = member.component();
            if (member.isAnnotation()) {
                collect(resolve(component, context), origin, context, closure);
            } else if (component.isEnum()) {
                requireAccessible(component, name, origin);
            }
        }
    }

    private static ClassElement resolve(ClassElement type, VisitorContext context) {
        // the declaration, which knows its members, rather than a usage
        return context.getClassElement(type.getName()).orElse(type);
    }

    private static void requireAccessible(ClassElement type, String annotationName, Element origin) {
        ClassElement current = type;
        while (current != null) {
            if (!current.isPublic()) {
                throw new ProcessingException(origin, "The annotation [" + annotationName + "] cannot have a builder: "
                    + (current == type ? "the type " : "the type that encloses ") + current.getName() + " is not public");
            }
            Optional<ClassElement> enclosing = current.getEnclosingType();
            current = enclosing.orElse(null);
        }
    }

    private void write(String holderName, ClassElement annotationType, VisitorContext context, Element... originatingElements) {
        String annotationName = annotationType.getName();
        String builderName = holderName + "$" + AnnotationBuilderRegistry.mangle(annotationName) + AnnotationBuilderRegistry.BUILDER_SUFFIX;
        String written = builders.putIfAbsent(builderName, annotationName);
        if (written != null) {
            if (!written.equals(annotationName)) {
                // the name of an annotation type is flattened in the name of its builder: Holder$A_B and Holder$A$B
                // both become Holder_A_B, so the builders of both would be the same class
                throw new ProcessingException(originatingElements[0], "The annotation [" + annotationName
                    + "] cannot have a builder next to " + holderName + ": its name flattens to the same builder name as the annotation ["
                    + written + "], which has one there. Register one of them from another type or package.");
            }
            return;
        }
        try {
            AnnotationBuilderWriter.write(holderName, annotationType, context, originatingElements);
        } catch (IOException e) {
            if (e instanceof FileAlreadyExistsException || ALREADY_WRITTEN.contains(e.getClass().getName())) {
                // written before by a processor or a round this registry does not know of: the builder is the same
                return;
            }
            builders.remove(builderName);
            throw new ProcessingException(originatingElements[0], "Failed to write the annotation builder of " + annotationName + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            // not written, such as when the originating element is postponed to the next round: the next
            // registration writes it
            builders.remove(builderName);
            throw e;
        }
    }

    private static String holderName(Element origin) {
        if (origin instanceof ClassElement classElement) {
            return classElement.getName();
        }
        if (origin instanceof MemberElement memberElement) {
            return memberElement.getOwningType().getName();
        }
        if (origin instanceof PackageElement packageElement) {
            return packageElement.getName() + PACKAGE_HOLDER;
        }
        throw new IllegalArgumentException("The originating element of an annotation builder has to be a type, a member of a type or a package: " + origin);
    }
}
