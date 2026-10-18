/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.visitor;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.reflect.GenericTypeUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;

import java.util.Collections;
import java.util.Set;

/**
 * Provides a hook into the compilation process to allow user defined functionality to be created at compile time.
 *
 * @param <C> The annotation required on the class. Use {@link Object} for all classes.
 * @param <E> The annotation required on the element. Use {@link Object} for all elements.
 * @author James Kleeh
 * @since 1.0
 */
public interface TypeElementVisitor<C, E> extends Ordered, Toggleable {

    /**
     * The query which allows to reduce the scope which the visitor is visiting.
     *
     * @return The query if the visitor.
     * @since 4.9
     */
    @Experimental
    default TypeElementQuery query() {
        return TypeElementQuery.DEFAULT;
    }

    /**
     * Executed when a class is encountered that matches the {@literal <}C{@literal >} generic.
     *
     * @param element The element
     * @param context The visitor context
     */
    default void visitClass(ClassElement element, VisitorContext context) {
        // no-op
    }

    /**
     * Executed when a method is encountered that matches the {@literal <}E{@literal >} generic.
     *
     * @param element The element
     * @param context The visitor context
     */
    default void visitMethod(MethodElement element, VisitorContext context) {
        // no-op
    }

    /**
     * Executed when a constructor is encountered that matches the {@literal <}C{@literal >} generic.
     *
     * @param element The element
     * @param context The visitor context
     */
    default void visitConstructor(ConstructorElement element, VisitorContext context) {
        // no-op
    }

    /**
     * Executed when a field is encountered that matches the {@literal <}E{@literal >} generic.
     *
     * @param element The element
     * @param context The visitor context
     */
    default void visitField(FieldElement element, VisitorContext context) {
        // no-op
    }

    /**
     * Executed when an enum constant is encountered that matches the {@literal <}E{@literal >} generic.
     *
     * @param element The element
     * @param context The visitor context
     *
     * @since 3.6.0
     */
    default void visitEnumConstant(EnumConstantElement element, VisitorContext context) {
        // no-op
    }

    /**
     * Called once when visitor processing starts, before any class is visited.
     *
     * @param visitorContext The visitor context
     */
    default void start(VisitorContext visitorContext) {
        // no-op
    }

    /**
     * Called at the end of each processing round, after every visitor has visited every class of the round.
     *
     * <p>A round is the set of classes a compiler hands to the visitors together. The classes of the compilation
     * form the first round, and the classes generated as source during a round form the next one. When this method
     * is called, no class of the round is visited anymore, and:</p>
     *
     * <ul>
     *     <li>a bean added with {@link VisitorContext#registerBean(ClassElement, io.micronaut.inject.ast.Element...)}
     *     or {@link ClassElement#addAssociatedBean(ClassElement)} is written with the beans of the round;</li>
     *     <li>a source written with {@link VisitorContext#visitGeneratedSourceFile(String, String, io.micronaut.inject.ast.Element...)}
     *     is compiled, and its classes are visited in a later round, followed by another call of this method.</li>
     * </ul>
     *
     * <p>The rounds of each compiler:</p>
     *
     * <ul>
     *     <li>javac: each annotation processing round, except the final one, in which nothing generated is
     *     processed anymore. {@link #finish(VisitorContext)} is called after this method in every round, the final one
     *     included.</li>
     *     <li>KSP: each call of {@code SymbolProcessor.process}. {@link #finish(VisitorContext)} is called once, after the
     *     last round.</li>
     *     <li>Groovy: the classes of the compilation are a round, visited during canonicalization, and so is each set of
     *     classes generated as source afterwards. {@link #finish(VisitorContext)} is called once, at class generation.</li>
     *     <li>Python: the compilation is a single round.</li>
     * </ul>
     *
     * <p>A round may visit no class matching this visitor, and the elements of a round are only guaranteed to be
     * usable during that round: KSP invalidates them when the round ends.</p>
     *
     * @param visitorContext The visitor context
     * @since 5.3.0
     */
    @Experimental
    default void finishRound(VisitorContext visitorContext) {
        // no-op
    }

    /**
     * Called when visitor processing finishes.
     *
     * <p>Under KSP and Groovy, this method is called once, when nothing generated as source is compiled anymore. Under
     * javac, it is called at the end of every annotation processing round, after {@link #finishRound(VisitorContext)},
     * the final round included. Prefer {@link #finishRound(VisitorContext)} for work that has to be done once the
     * classes of a round are visited: under KSP the elements of the rounds are invalid by the time this method is
     * called, and no bean can be registered from it.</p>
     *
     * @param visitorContext The visitor context
     */
    default void finish(VisitorContext visitorContext) {
        // no-op
    }

    /**
     * @return The supported default annotation names.
     */
    default Set<String> getSupportedAnnotationNames() {
        Class<?>[] classes = GenericTypeUtils.resolveInterfaceTypeArguments(getClass(), TypeElementVisitor.class);

        if (classes.length == 2) {
            Class<?> classType = classes[0];
            String classTypeName = classType.getName();
            if (classType == Object.class) {
                classTypeName = getClassType();
            }
            if (classTypeName.equals(Object.class.getName())) {
                return Collections.singleton("*");
            } else {
                Class<?> elementType = classes[1];
                String elementTypeName = elementType.getName();
                if (elementTypeName.equals(Object.class.getName())) {
                    elementTypeName = getElementType();
                }
                if (elementTypeName.equals(Object.class.getName())) {
                    return CollectionUtils.setOf(classTypeName);
                } else {
                    return CollectionUtils.setOf(classTypeName, elementTypeName);
                }
            }
        }
        return Collections.singleton("*");
    }

    default String getClassType() {
        return Object.class.getName();
    }

    default String getElementType() {
        return Object.class.getName();
    }

    /**
     * Called once when processor loads.
     *
     * Used to expose visitors custom processor options.
     *
     * @return Set with custom options
     */
    @Experimental
    default Set<String> getSupportedOptions() {
        return Collections.emptySet();
    }

    /**
     * @return The visitor kind.
     */
    default VisitorKind getVisitorKind() {
        return VisitorKind.AGGREGATING;
    }

    /**
     * Implementors of the {@link TypeElementVisitor} interface should specify what kind of visitor it is.
     *
     * If the visitor looks at multiple {@link io.micronaut.inject.ast.Element} and builds a file that references
     * multiple {@link io.micronaut.inject.ast.Element} (meaning it doesn't have an originating element) then
     * {@link VisitorKind#AGGREGATING} should be used
     *
     * If the visitor generates classes from an originating {@link io.micronaut.inject.ast.Element} then {@link VisitorKind#ISOLATING} should be used.
     */
    enum VisitorKind {
        /**
         * A visitor that generates a file for each visited element and calls.
         */
        ISOLATING,
        /**
         * A visitor that generates a one or more files in the {@link #finish(VisitorContext)} method computed from visiting multiple {@link io.micronaut.inject.ast.Element} instances.
         */
        AGGREGATING
    }
}
