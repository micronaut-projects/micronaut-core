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
 * Stores data about a compile time element. The underlying object can be a class, field, or method.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface Element extends AnnotationMetadata {

    /**
     * @return The name of the element.
     */
    String getName();

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
    Object getNativeType();

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
}
