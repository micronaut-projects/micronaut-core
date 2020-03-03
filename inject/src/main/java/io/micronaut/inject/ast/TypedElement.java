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
package io.micronaut.inject.ast;

import javax.annotation.Nonnull;

/**
 * An element that has an underlying type.
 *
 * @author graemerocher
 * @since 1.0
 * @see PropertyElement
 * @see ClassElement
 * @see FieldElement
 */
public interface TypedElement extends Element {
    /**
     * @return The type of the element
     */
    @Nonnull
    ClassElement getType();

    /**
     * Returns the generic type of the element. This differs from {@link #getType()} as it returns
     * the actual type without erasure. Whilst {@link #getType()} is often needed to produce the correct byte code when
     * generating code via ASM, the {@code getGenericType()} method is more useful for documentation and other types of code
     * generation.
     *
     * @return The generic type
     * @since 1.1.1
     */
    @Nonnull
    default ClassElement getGenericType() {
        return getType();
    }

    /**
     * Whether the type is primitive.
     * @return True if it is
     */
    default boolean isPrimitive() {
        return false;
    }

    /**
     * Is the type an array.
     * @return True if it is.
     */
    default boolean isArray() {
        return false;
    }
}
