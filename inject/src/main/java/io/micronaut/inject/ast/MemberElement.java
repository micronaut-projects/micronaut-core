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

import java.util.Collections;
import java.util.Set;

import io.micronaut.core.annotation.NonNull;

/**
 * A member element is an element that is contained within a {@link ClassElement}.
 * The {@link #getDeclaringType()} method returns the class that declares the element.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface MemberElement extends Element {

    /**
     * @return The declaring type of the element.
     */
    ClassElement getDeclaringType();

    /**
     * The owing type is the type that owns this element. This can differ from {@link #getDeclaringType()}
     * in the case of inheritance since this method will return the subclass that owners the inherited member,
     * whilst {@link #getDeclaringType()} will return the super class that declares the type.
     *
     * @return The owning type.
     */
    default ClassElement getOwningType() {
        return getDeclaringType();
    }

    /**
     * @return The {@link ElementModifier} types for this class element
     * @since 3.0.0
     */
    default Set<ElementModifier> getModifiers() {
        return Collections.emptySet();
    }

    /**
     * Returns whether this member element will require reflection to invoke or retrieve at runtime.
     *
     * <p>This method uses {@link #getOwningType()} as the calling type for this method.</p>
     *
     * @return Will return {@code true} if reflection is required.
     * @since 3.4.0
     */
    default boolean isReflectionRequired() {
        return isReflectionRequired(getOwningType());
    }

    /**
     * Returns whether this member element will require reflection to invoke or retrieve at runtime.
     *
     * @param callingType The calling type
     * @return Will return {@code true} if reflection is required.
     * @since 3.4.0
     */
    default boolean isReflectionRequired(@NonNull ClassElement callingType) {
        if (isPublic()) {
            return false;
        } else {
            if (isPackagePrivate() || isProtected()) {
                // the declaring type might be a super class in which
                // case if the super class is in a different package then
                // the method or field is not visible and hence reflection is required
                final ClassElement declaringType = getDeclaringType();
                return !declaringType.getPackageName().equals(callingType.getPackageName());
            } else {
                return true;
            }
        }
    }
}
