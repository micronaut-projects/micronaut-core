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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;

/**
 * Stores data about an element that references a field.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface FieldElement extends TypedElement, MemberElement {

    /**
     * Returns the value of this variable if this is a {@code final}
     * field initialized to a compile-time constant.  Returns {@code
     * null} otherwise.  The value will be of a primitive type or a
     * {@code String}.  If the value is of a primitive type, it is
     * wrapped in the appropriate wrapper class (such as {@link
     * Integer}).
     *
     * <p>Note that not all {@code final} fields will have
     * constant values. In particular, {@code enum} constants are
     * <em>not</em> considered to be compile-time constants. To have a
     * constant value, a field's type must be either a primitive type
     * or {@code String}.
     *
     * @return the value of this variable if this is a {@code final}
     * field initialized to a compile-time constant, or {@code null}
     * otherwise
     * @since 4.5.0
     */
    default Object getConstantValue() {
        return null;
    }

    /**
     * Obtain the generic type with the associated annotation metadata for the field.
     *
     * @return The generic field
     */
    default ClassElement getGenericField() {
        return getGenericType();
    }

    @NonNull
    @Override
    default String getDescription(boolean simple) {
        if (simple) {
            return getType().getSimpleName() + " " + getName();
        } else {
            return getType().getName() + " " + getName();
        }
    }

    @Override
    default FieldElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (FieldElement) MemberElement.super.withAnnotationMetadata(annotationMetadata);
    }

    @Override
    default boolean hides(@NonNull MemberElement memberElement) {
        if (memberElement instanceof FieldElement hidden) {
            if (equals(hidden) || isStatic() && getDeclaringType().isInterface() || hidden.isPrivate()) {
                return false;
            }
            if (!getName().equals(hidden.getName()) || !getDeclaringType().isAssignable(hidden.getDeclaringType())) {
                return false;
            }
            if (hidden.isPackagePrivate()) {
                return getDeclaringType().getPackageName().equals(hidden.getDeclaringType().getPackageName());
            }
            return true;
        }
        return false;
    }
}
