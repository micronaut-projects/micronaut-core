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

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.Described;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Stores data about a compile time element. The underlying object can be a class, field, or method.
 *
 * @author James Kleeh
 * @author graemerocher
 * @since 1.0
 */
public interface Element extends MutableAnnotatedElement<Element>, AnnotatedElement, AnnotationMetadataDelegate, Described {

    /**
     * An empty array of elements.
     * @since 2.1.1
     */
    Element[] EMPTY_ELEMENT_ARRAY = new Element[0];

    /**
     * @return The name of the element. For a type this represents the binary name.
     */
    @Override
    @NonNull String getName();

    /**
     * @return True if the element is package private.
     * @since 2.3.0
     */
    default boolean isPackagePrivate() {
        return false;
    }

    /**
     * @return True if the element is synthetic.
     * @since 4.0.0
     */
    default boolean isSynthetic() {
        return false;
    }

    /**
     * @return True if the element is protected.
     */
    boolean isProtected();

    /**
     * @return True if the element is public.
     */
    boolean isPublic();

    /**
     * Returns the native underlying type. This API is extended by all of the inject language implementations.
     * The object returned by this method will be the language native type the information is being retrieved from.
     *
     * @return The native type
     */
    @NonNull Object getNativeType();

    /**
     * @return The {@link ElementModifier} types for this class element
     * @since 3.0.0
     */
    default Set<ElementModifier> getModifiers() {
        return Collections.emptySet();
    }

    /**
     * The simple name of the element. For a class this will be the name without the package.
     *
     * @return The simple name
     */
    default @NonNull String getSimpleName() {
        return getName();
    }

    /**
     * @return True if the element is abstract.
     */
    default boolean isAbstract() {
        return false;
    }

    /**
     * @return True if the element is static.
     */
    default boolean isStatic() {
        return false;
    }

    /**
     * @return The documentation, if any.
     */
    default Optional<String> getDocumentation() {
        return Optional.empty();
    }

    /**
     * @return True if the element is private.
     */
    default boolean isPrivate() {
        return !isPublic();
    }

    /**
     * @return True if the element is final.
     */
    default boolean isFinal() {
        return false;
    }

    @NonNull
    @Override
    default String getDescription() {
        return getDescription(true);
    }

    @NonNull
    @Override
    default String getDescription(boolean simple) {
        if (simple) {
            return getSimpleName();
        } else {
            return getName();
        }
    }

    /**
     * Copies this element and overrides its annotations.
     *
     * @param annotationMetadata The annotation metadata
     * @return A new element
     * @since 4.0.0
     */
    default Element withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        throw new UnsupportedOperationException("Element of type [" + getClass() + "] does not support copy constructor");
    }
}
