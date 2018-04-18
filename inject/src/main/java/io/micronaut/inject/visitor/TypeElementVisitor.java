/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata;

/**
 * Provides a hook into the compilation process to allow user
 * defined functionality to be created at compile time.
 *
 * @param <C> The annotation required on the class.
 * @param <E> The annotation required on the element.
 * @author James Kleeh
 * @since 1.0
 */
public interface TypeElementVisitor<C, E> {

    /**
     * Executed when a class is encountered that matches the <C> generic
     *
     * @param element The element
     * @param annotationMetadata The annotation metadata
     * @param context The visitor context
     */
    void visitClass(ClassElement element, AnnotationMetadata annotationMetadata, VisitorContext context);

    /**
     * Executed when a method is encountered that matches the <E> generic
     *
     * @param element The element
     * @param annotationMetadata The annotation metadata
     * @param context The visitor context
     */
    void visitMethod(MethodElement element, AnnotationMetadata annotationMetadata, VisitorContext context);

    /**
     * Executed when a field is encountered that matches the <E> generic
     *
     * @param element The element
     * @param annotationMetadata The annotation metadata
     * @param context The visitor context
     */
    void visitField(FieldElement element, AnnotationMetadata annotationMetadata, VisitorContext context);
}
