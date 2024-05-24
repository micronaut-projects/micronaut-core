/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;

import java.util.Optional;

/**
 * Represents a generic element that can appear as a type argument.
 *
 * @since 4.0.0
 * @author Denis Stepanov
 */
@Experimental
public interface GenericElement extends ClassElement {

    /**
     * The native type that represents the generic element.
     * It is expected that the generic element representing `T` extends java.lang.Number`
     * should be equal to the class element `java.lang.Number`.
     * To find matching placeholders we can use this method to match the native generic type.
     *
     * @return The generic native type
     */
    @NonNull
    default Object getGenericNativeType() {
        return getNativeType();
    }

    /**
     * Returns the type parameter annotations.
     * Added to this generic element by:
     * - The declaration of the type variable {@link java.lang.annotation.ElementType#TYPE_PARAMETER}
     * - The use of the type {@link java.lang.annotation.ElementType#TYPE}
     * @return the type annotations
     */
    @Experimental
    @NonNull
    default MutableAnnotationMetadataDelegate<AnnotationMetadata> getGenericTypeAnnotationMetadata() {
        return (MutableAnnotationMetadataDelegate<AnnotationMetadata>) MutableAnnotationMetadataDelegate.EMPTY;
    }

    /**
     * In some cases the class element can be a resolved as a generic element and this element should return the actual
     * type that is generic element is representing.
     *
     * @return The resolved value of the generic element.
     * @since 4.2.0
     */
    default Optional<ClassElement> getResolved() {
        return Optional.empty();
    }

    /**
     * Tries to resolve underneath type using {@link #getResolved()} or returns this type otherwise.
     *
     * @return The resolved value of the generic element or this type.
     * @since 4.2.0
     */
    default ClassElement resolved() {
        return getResolved().orElse(this);
    }

}
