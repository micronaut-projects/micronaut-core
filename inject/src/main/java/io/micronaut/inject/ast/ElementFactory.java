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

import io.micronaut.core.annotation.NonNull;

import java.util.Map;

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
     * @param type                      The type
     * @param annotationMetadataFactory The element annotation metadata factory
     * @return The class element
     * @since 4.0.0
     */
    @NonNull
    ClassElement newClassElement(@NonNull C type, ElementAnnotationMetadataFactory annotationMetadataFactory);

    /**
     * Builds a new class element for the given type.
     *
     * @param type                      The type
     * @param annotationMetadataFactory The element annotation metadata factory
     * @param resolvedGenerics          The resolved generics
     * @return The class element
     * @since 4.0.0
     */
    @NonNull
    ClassElement newClassElement(@NonNull C type,
                                 @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory,
                                 @NonNull Map<String, ClassElement> resolvedGenerics);

    /**
     * Builds a new source class element for the given type. This method
     * differs from {@link #newClassElement(Object, ElementAnnotationMetadataFactory)} in that
     * it should only be called from elements that are known to originate from source code.
     *
     * @param type                             The type
     * @param elementAnnotationMetadataFactory The element annotation metadata factory
     * @return The class element
     * @since 4.0.0
     */
    @NonNull
    ClassElement newSourceClassElement(@NonNull C type, @NonNull ElementAnnotationMetadataFactory elementAnnotationMetadataFactory);

    /**
     * Builds a new source method element for the given method. This method
     * differs from {@link #newMethodElement(ClassElement, Object, ElementAnnotationMetadataFactory)} in that
     * it should only be called from elements that are known to originate from source code.
     *
     * @param owningClass                      The owning class
     * @param method                           The method
     * @param elementAnnotationMetadataFactory The element annotation metadata factory
     * @return The class element
     * @since 4.0.0
     */
    @NonNull
    MethodElement newSourceMethodElement(@NonNull ClassElement owningClass,
                                         @NonNull M method,
                                         @NonNull ElementAnnotationMetadataFactory elementAnnotationMetadataFactory);

    /**
     * Builds a new method element for the given type.
     *
     * @param owningClass                      The owning class
     * @param method                           The method
     * @param elementAnnotationMetadataFactory The element annotation metadata factory
     * @return The method element
     * @since 4.0.0
     */
    @NonNull
    MethodElement newMethodElement(@NonNull ClassElement owningClass,
                                   @NonNull M method,
                                   @NonNull ElementAnnotationMetadataFactory elementAnnotationMetadataFactory);

    /**
     * Builds a new constructor element for the given type.
     *
     * @param owningClass                      The owning class
     * @param constructor                      The constructor
     * @param elementAnnotationMetadataFactory The element annotation metadata factory
     * @return The constructor element
     * @since 4.0.0
     */
    @NonNull
    ConstructorElement newConstructorElement(@NonNull ClassElement owningClass,
                                             @NonNull M constructor,
                                             @NonNull ElementAnnotationMetadataFactory elementAnnotationMetadataFactory);

    /**
     * Builds a new enum constant element for the given type.
     *
     * @param owningClass                      The owning class
     * @param enumConstant                     The enum constant
     * @param elementAnnotationMetadataFactory The element annotation metadata factory
     * @return The enum constant element
     * @since 4.0.0
     */
    @NonNull
    EnumConstantElement newEnumConstantElement(@NonNull ClassElement owningClass,
                                               @NonNull F enumConstant,
                                               @NonNull ElementAnnotationMetadataFactory elementAnnotationMetadataFactory);

    /**
     * Builds a new field element for the given type.
     *
     * @param owningClass                      The owning class
     * @param field                            The field
     * @param elementAnnotationMetadataFactory The element annotation metadata factory
     * @return The field element
     * @since 4.0.0
     */
    @NonNull
    FieldElement newFieldElement(@NonNull ClassElement owningClass,
                                 @NonNull F field,
                                 @NonNull ElementAnnotationMetadataFactory elementAnnotationMetadataFactory);

}
