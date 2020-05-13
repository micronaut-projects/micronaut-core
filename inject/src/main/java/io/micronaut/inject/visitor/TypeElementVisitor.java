/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.visitor;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.Ordered;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
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
public interface TypeElementVisitor<C, E> extends Ordered {

    /**
     * Executed when a class is encountered that matches the <C> generic.
     *
     * @param element The element
     * @param context The visitor context
     */
    default void visitClass(ClassElement element, VisitorContext context) {
        // no-op
    }

    /**
     * Executed when a method is encountered that matches the <E> generic.
     *
     * @param element The element
     * @param context The visitor context
     */
    default void visitMethod(MethodElement element, VisitorContext context) {
        // no-op
    }


    /**
     * Executed when a constructor is encountered that matches the <C> generic.
     *
     * @param element The element
     * @param context The visitor context
     */
    default void visitConstructor(ConstructorElement element, VisitorContext context) {
        // no-op
    }

    /**
     * Executed when a field is encountered that matches the <E> generic.
     *
     * @param element The element
     * @param context The visitor context
     */
    default void visitField(FieldElement element, VisitorContext context) {
        // no-op
    }

    /**
     * Called once when visitor processing starts.
     *
     * @param visitorContext The visitor context
     */
    default void start(VisitorContext visitorContext) {
        // no-op
    }

    /**
     * Called once when visitor processing finishes.
     *
     * @param visitorContext The visitor context
     */
    default void finish(VisitorContext visitorContext) {
        // no-op
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
    default @NonNull VisitorKind getVisitorKind() {
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
         * A visitor that generates a file for each visited element and calls
         */
        ISOLATING,
        /**
         * A visitor that generates a one or more files in the {@link #finish(VisitorContext)} method computed from visiting multiple {@link io.micronaut.inject.ast.Element} instances.
         */
        AGGREGATING
    }
}
