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
package io.micronaut.inject.ast;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;

/**
 * A factory for creating elements.
 *
 * @param <E> The type that represents the super type of all elements in the AST
 * @param <C> The type that represents a class in the AST
 * @param <M> The type that represents a method in the AST
 * @param <F> The type that represents a field in the AST
 * @author graemerocher
 * @since 2.3.0
 */
public interface ElementFactory<E, C extends E, M extends E, F extends E> {
    /**
     * Builds a new class element for the given type.
     *
     * @param type               The type
     * @param annotationMetadata The resolved annotation metadata
     * @return The class element
     */
    @NonNull
    ClassElement newClassElement(
            @NonNull C type,
            @NonNull AnnotationMetadata annotationMetadata);

    /**
     * Builds a new method element for the given type.
     *
     * @param declaringClass     The declaring class
     * @param method             The method
     * @param annotationMetadata The resolved annotation metadata
     * @return The method element
     */
    @NonNull
    MethodElement newMethodElement(
            ClassElement declaringClass,
            @NonNull M method,
            @NonNull AnnotationMetadata annotationMetadata);


    /**
     * Builds a new constructor element for the given type.
     *
     * @param declaringClass     The declaring class
     * @param constructor        The constructor
     * @param annotationMetadata The resolved annotation metadata
     * @return The constructor element
     */
    @NonNull
    ConstructorElement newConstructorElement(
            ClassElement declaringClass,
            @NonNull M constructor,
            @NonNull AnnotationMetadata annotationMetadata);

    /**
     * Builds a new field element for the given type.
     *
     * @param declaringClass     The declaring class
     * @param field              The field
     * @param annotationMetadata The resolved annotation metadata
     * @return The field element
     */
    @NonNull
    FieldElement newFieldElement(
            ClassElement declaringClass,
            @NonNull F field,
            @NonNull AnnotationMetadata annotationMetadata);

    /**
     * Builds a new field element for the given field.
     *
     * @param field              The field
     * @param annotationMetadata The resolved annotation metadata
     * @return The field element
     */
    @NonNull
    FieldElement newFieldElement(
            @NonNull F field,
            @NonNull AnnotationMetadata annotationMetadata);

}
