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
import io.micronaut.core.annotation.AnnotationMetadata;

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
     * @param type               The type
     * @param annotationMetadata The resolved annotation metadata
     * @return The class element
     * @deprecated use {@link #newClassElement(Object)}
     */
    @Deprecated
    @NonNull
    ClassElement newClassElement(
        @NonNull C type,
        @NonNull AnnotationMetadata annotationMetadata);

    /**
     * Builds a new class element for the given type.
     *
     * @param type The type
     * @return The class element
     */
    @NonNull
    ClassElement newClassElement(@NonNull C type, ElementAnnotationMetadataFactory annotationMetadataFactory);

    /**
     * Builds a new class element for the given type.
     *
     * @param type               The type
     * @param annotationMetadata The resolved annotation metadata
     * @param resolvedGenerics   The resolved generics
     * @return The class element
     * @since 3.8.0
     * @deprecated user {@link #newClassElement(Object, ElementAnnotationMetadataFactory, Map)}
     */
    @NonNull
    ClassElement newClassElement(
        @NonNull C type,
        @NonNull AnnotationMetadata annotationMetadata,
        @NonNull Map<String, ClassElement> resolvedGenerics);

    /**
     * Builds a new class element for the given type.
     *
     * @param type             The type
     * @param resolvedGenerics The resolved generics
     * @return The class element
     * @since 3.8.0
     */
    @NonNull
    ClassElement newClassElement(
        @NonNull C type,
        @NonNull ElementAnnotationMetadataFactory annotationMetadataFactory,
        @NonNull Map<String, ClassElement> resolvedGenerics);

    /**
     * Builds a new source class element for the given type. This method
     * differs from {@link #newClassElement(Object, AnnotationMetadata)} in that
     * it should only be called from elements that are known to originate from source code.
     *
     * @param type               The type
     * @param annotationMetadata The resolved annotation metadata
     * @return The class element
     * @since 3.0.0
     * @deprecated user {@link #newSourceClassElement(Object)}
     */
    @Deprecated
    @NonNull
    ClassElement newSourceClassElement(
        @NonNull C type,
        @NonNull AnnotationMetadata annotationMetadata);

    /**
     * Builds a new source class element for the given type. This method
     * differs from {@link #newClassElement(Object, AnnotationMetadata)} in that
     * it should only be called from elements that are known to originate from source code.
     *
     * @param type The type
     * @return The class element
     * @since 3.0.0
     */
    @NonNull
    ClassElement newSourceClassElement(@NonNull C type, @NonNull ElementAnnotationMetadataFactory elementAnnotationMetadataFactory);

    /**
     * Builds a new source method element for the given method. This method
     * differs from {@link #newMethodElement(ClassElement, Object, AnnotationMetadata)} in that
     * it should only be called from elements that are known to originate from source code.
     *
     * @param owningClass        The owning class
     * @param method             The method
     * @param annotationMetadata The resolved annotation metadata
     * @return The class element
     * @since 3.0.0
     * @deprecated use {@link #newSourceMethodElement(ClassElement, Object)}
     */
    @Deprecated
    @NonNull
    MethodElement newSourceMethodElement(
        ClassElement owningClass,
        @NonNull M method,
        @NonNull AnnotationMetadata annotationMetadata);

    /**
     * Builds a new source method element for the given method. This method
     * differs from {@link #newMethodElement(ClassElement, Object, AnnotationMetadata)} in that
     * it should only be called from elements that are known to originate from source code.
     *
     * @param owningClass The owning class
     * @param method      The method
     * @return The class element
     * @since 3.0.0
     */
    @NonNull
    MethodElement newSourceMethodElement(
        @NonNull ClassElement owningClass,
        @NonNull M method,
        @NonNull ElementAnnotationMetadataFactory elementAnnotationMetadataFactory);

    /**
     * Builds a new method element for the given type.
     *
     * @param owningClass        The owning class
     * @param method             The method
     * @param annotationMetadata The resolved annotation metadata
     * @return The method element
     * @deprecated use {@link #newMethodElement(ClassElement, Object)}
     */
    @Deprecated
    @NonNull
    MethodElement newMethodElement(
        @NonNull ClassElement owningClass,
        @NonNull M method,
        @NonNull AnnotationMetadata annotationMetadata);

    /**
     * Builds a new method element for the given type.
     *
     * @param owningClass The owning class
     * @param method      The method
     * @return The method element
     */
    @NonNull
    MethodElement newMethodElement(
        @NonNull ClassElement owningClass,
        @NonNull M method,
        @NonNull ElementAnnotationMetadataFactory elementAnnotationMetadataFactory);

    /**
     * Builds a new constructor element for the given type.
     *
     * @param owningClass        The owning class
     * @param constructor        The constructor
     * @param annotationMetadata The resolved annotation metadata
     * @return The constructor element
     * @deprecated user {@link #newConstructorElement(ClassElement, Object)}
     */
    @Deprecated
    @NonNull
    ConstructorElement newConstructorElement(
        @NonNull ClassElement owningClass,
        @NonNull M constructor,
        @NonNull AnnotationMetadata annotationMetadata);

    /**
     * Builds a new constructor element for the given type.
     *
     * @param owningClass The owning class
     * @param constructor The constructor
     * @return The constructor element
     */
    @NonNull
    ConstructorElement newConstructorElement(
        @NonNull ClassElement owningClass,
        @NonNull M constructor,
        @NonNull ElementAnnotationMetadataFactory elementAnnotationMetadataFactory);

    /**
     * Builds a new enum constant element for the given type.
     *
     * @param owningClass        The owning class
     * @param enumConstant       The enum constant
     * @param annotationMetadata The resolved annotation metadata
     * @return The enum constant element
     * @since 3.6.0
     * @deprecated use {@link #newEnumConstantElement(ClassElement, Object)}
     */
    @Deprecated
    @NonNull
    EnumConstantElement newEnumConstantElement(
        @NonNull ClassElement owningClass,
        @NonNull F enumConstant,
        @NonNull AnnotationMetadata annotationMetadata);

    /**
     * Builds a new enum constant element for the given type.
     *
     * @param owningClass  The owning class
     * @param enumConstant The enum constant
     * @return The enum constant element
     * @since 3.6.0
     */
    @NonNull
    EnumConstantElement newEnumConstantElement(
        @NonNull ClassElement owningClass,
        @NonNull F enumConstant,
        @NonNull ElementAnnotationMetadataFactory elementAnnotationMetadataFactory);

    /**
     * Builds a new field element for the given type.
     *
     * @param owningClass        The owning class
     * @param field              The field
     * @param annotationMetadata The resolved annotation metadata
     * @return The field element
     * @deprecated use {@link #newFieldElement(ClassElement, Object)}
     */
    @Deprecated
    @NonNull
    FieldElement newFieldElement(
        @NonNull ClassElement owningClass,
        @NonNull F field,
        @NonNull AnnotationMetadata annotationMetadata);

    /**
     * Builds a new field element for the given type.
     *
     * @param owningClass The owning class
     * @param field       The field
     * @return The field element
     */
    @NonNull
    FieldElement newFieldElement(
        @NonNull ClassElement owningClass,
        @NonNull F field,
        @NonNull ElementAnnotationMetadataFactory elementAnnotationMetadataFactory);

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
