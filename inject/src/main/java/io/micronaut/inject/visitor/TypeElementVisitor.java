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

import io.micronaut.core.order.Ordered;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;

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
}
