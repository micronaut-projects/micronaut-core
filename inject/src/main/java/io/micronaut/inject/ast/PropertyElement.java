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

import java.util.Optional;

/**
 * A property element represents a JavaBean property on a {@link ClassElement}.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface PropertyElement extends TypedElement, MemberElement {

    /**
     * @return The type of the property
     */
    @NonNull
    @Override
    ClassElement getType();

    /**
     * Return true the property is excluded.
     *
     * @return True if the property is excluded
     * @since 4.0.0
     */
    default boolean isExcluded() {
        return false;
    }

    /**
     * Return true only if the property has a getter but no setter.
     *
     * @return True if the property is read only.
     */
    default boolean isReadOnly() {
        return !getWriteMethod().isPresent();
    }

    /**
     * Return true only if the property doesn't support modifying the value.
     *
     * @return True if the property is write only.
     * @since 4.0.0
     */
    default boolean isWriteOnly() {
        return !getReadMethod().isPresent();
    }

    /**
     * The field representing the property.
     * NOTE: The field can be returned even if getter/setter are present.
     *
     * @return The field
     * @since 4.0.0
     */
    default Optional<FieldElement> getField() {
        return Optional.empty();
    }

    /**
     * @return The method to write the property
     * @since 4.0.0
     */
    default Optional<MethodElement> getWriteMethod() {
        return Optional.empty();
    }

    /**
     * @return The method to read the property
     */
    default Optional<MethodElement> getReadMethod() {
        return Optional.empty();
    }

    /**
     * @return The member to read the property
     * @since 4.0.0
     */
    default Optional<? extends MemberElement> getReadMember() {
        if (getReadAccessKind() == AccessKind.METHOD) {
            return getReadMethod();
        }
        return getField();
    }

    /**
     * @return The member to write the property
     * @since 4.0.0
     */
    default Optional<? extends MemberElement> getWriteMember() {
        if (getWriteAccessKind() == AccessKind.METHOD) {
            return getWriteMethod();
        }
        return getField();
    }

    /**
     * @return The read access kind of the property
     * @since 4.0.0
     */
    default AccessKind getReadAccessKind() {
        return AccessKind.METHOD;
    }

    /**
     * @return The write access kind of the property
     * @since 4.0.0
     */
    default AccessKind getWriteAccessKind() {
        return AccessKind.METHOD;
    }

    /**
     * The access type for bean properties.
     * @since 4.0.0
     */
    enum AccessKind {
        /**
         * Allows the use of public or package-protected fields to represent bean properties.
         */
        FIELD,
        /**
         * The default behaviour which is to favour public getters for bean properties.
         */
        METHOD
    }

}
