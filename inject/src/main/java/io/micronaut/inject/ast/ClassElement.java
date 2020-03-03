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

import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArgumentUtils;
import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Predicate;

/**
 * Stores data about an element that references a class.
 *
 * @author James Kleeh
 * @author graemerocher
 * @since 1.0
 */
public interface ClassElement extends TypedElement {

    /**
     * Tests whether one type is assignable to another.
     *
     * @param type The type to check
     * @return {@code true} if and only if the this type is assignable to the second
     */
    boolean isAssignable(String type);

    /**
     * Whether this element is an enum.
     * @return True if it is an enum
     */
    default boolean isEnum() {
        return this instanceof EnumElement;
    }

    /**
     * Find and return a single primary constructor. If more than constructor candidate exists, then return empty unless a
     * constructor is found that is annotated with either {@link io.micronaut.core.annotation.Creator} or {@link javax.inject.Inject}.
     *
     * @return The primary constructor if one is present
     */
    default @Nonnull Optional<MethodElement> getPrimaryConstructor() {
        return Optional.empty();
    }

    /**
     * Find and return a single default constructor. A default constructor is one
     * without arguments that is accessible.
     *
     * @return The default constructor if one is present
     */
    default @Nonnull Optional<MethodElement> getDefaultConstructor() {
        return Optional.empty();
    }

    /**
     * Returns the super type of this element or empty if the element has no super type.
     *
     * @return An optional of the super type
     */
    default Optional<ClassElement> getSuperType() {
        return Optional.empty();
    }

    @Nonnull
    @Override
    default ClassElement getType() {
        return this;
    }

    /**
     * The simple name without the package name.
     *
     * @return The simple name
     */
    @Override
    default String getSimpleName() {
        return NameUtils.getSimpleName(getName());
    }

    /**
     * The package name.
     *
     * @return The package name
     */
    default String getPackageName() {
        return NameUtils.getPackageName(getName());
    }

    /**
     * Returns the bean properties (getters and setters) for this class element.
     *
     * @return The bean properties for this class element
     */
    default List<PropertyElement> getBeanProperties() {
        return Collections.emptyList();
    }

    /**
     * Return all the fields of this class element.
     *
     * @return The fields
     */
    default List<FieldElement> getFields() {
        return getFields((modifiers) -> true);
    }

    /**
     * Return fields contained with the given modifiers include / exclude rules.
     *
     * @param modifierFilter Can be used to filter fields by modifier
     * @return The fields
     */
    default List<FieldElement> getFields(@Nonnull Predicate<Set<ElementModifier>> modifierFilter) {
        return Collections.emptyList();
    }

    /**
     * @return Whether the class element is abstract
     */
    default boolean isAbstract() {
        return false;
    }

    /**
     * @return Whether the class element is an interface
     */
    default boolean isInterface() {
        return false;
    }

    /**
     * Returns whether the class element is an array.
     *
     * @return True if this class element is an array
     */
    @Override
    default boolean isArray() {
        return false;
    }

    /**
     * @return Whether the type is iterable (either an array or an Iterable)
     */
    default boolean isIterable() {
        return isArray() || isAssignable(Iterable.class);
    }

    /**
     * Get the type arguments for the given type name.
     *
     * @param type The type to retrieve type arguments for
     * @return The type arguments for this class element
     * @since 1.1.1
     */
    default @Nonnull Map<String, ClassElement> getTypeArguments(@Nonnull String type) {
        return Collections.emptyMap();
    }

    /**
     * Get the type arguments for the given type name.
     *
     * @param type The type to retrieve type arguments for
     * @return The type arguments for this class element
     */
    default @Nonnull Map<String, ClassElement> getTypeArguments(@Nonnull Class<?> type) {
        ArgumentUtils.requireNonNull("type", type);
        return getTypeArguments(type.getName());
    }

    /**
     * @return The type arguments for this class element
     */
    default @Nonnull Map<String, ClassElement> getTypeArguments() {
        return Collections.emptyMap();
    }

    /**
     * @return The first type argument
     */
    default Optional<ClassElement> getFirstTypeArgument() {
        return getTypeArguments().values().stream().findFirst();
    }

    /**
     * Tests whether one type is assignable to another.
     *
     * @param type The type to check
     * @return {@code true} if and only if the this type is assignable to the second
     */
    default boolean isAssignable(Class<?> type) {
        return isAssignable(type.getName());
    }

}
