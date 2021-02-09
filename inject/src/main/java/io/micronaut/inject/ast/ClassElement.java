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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.annotation.NonNull;
import org.jetbrains.annotations.NotNull;

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
     * Tests whether one type is assignable to another.
     *
     * @param type The type to check
     * @return {@code true} if and only if the this type is assignable to the second
     * @since 2.3.0
     */
    default boolean isAssignable(ClassElement type) {
        return isAssignable(type.getName());
    }

    /**
     * Whether this element is an {@link Optional}.
     *
     * @return Is this element an optional
     * @since 2.3.0
     */
    default boolean isOptional() {
        return isAssignable(Optional.class);
    }

    /**
     * This method will return the name of the underlying type automatically unwrapping in the case of an optional
     * or wrapped representation of the type.
     *
     * @return Returns the canonical name of the type.
     * @since 2.3.0
     */
    default String getCanonicalName() {
        if (isOptional()) {
            return getFirstTypeArgument().map(ClassElement::getName).orElse(Object.class.getName());
        } else {
            return getName();
        }
    }

    /**
     * @return Whether this element is a record
     * @since 2.1.0
     */
    default boolean isRecord() {
        return false;
    }

    /**
     * Is this type an inner class.
     * @return True if it is an inner class
     * @since 2.1.2
     */
    default boolean isInner() {
        return false;
    }

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
    default @NonNull Optional<MethodElement> getPrimaryConstructor() {
        return Optional.empty();
    }

    /**
     * Find and return a single default constructor. A default constructor is one
     * without arguments that is accessible.
     *
     * @return The default constructor if one is present
     */
    default @NonNull Optional<MethodElement> getDefaultConstructor() {
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

    @NonNull
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
        return getEnclosedElements(ElementQuery.ALL_FIELDS);
    }

    /**
     * Return fields contained with the given modifiers include / exclude rules.
     *
     * @param modifierFilter Can be used to filter fields by modifier
     * @return The fields
     * @deprecated Use {@link #getEnclosedElements(ElementQuery)} instead
     */
    @Deprecated
    default List<FieldElement> getFields(@NonNull Predicate<Set<ElementModifier>> modifierFilter) {
        Objects.requireNonNull(modifierFilter, "The modifier filter cannot be null");
        return getEnclosedElements(ElementQuery.ALL_FIELDS.modifiers(modifierFilter));
    }

    /**
     * Return the elements that match the given query.
     *
     * @param query The query to use.
     * @param <T>  The element type
     * @return The fields
     * @since 2.3.0
     */
    default <T extends Element> List<T> getEnclosedElements(@NonNull ElementQuery<T> query) {
        return Collections.emptyList();
    }

    /**
     * Return the first enclosed element matching the given query.
     *
     * @param query The query to use.
     * @param <T>  The element type
     * @return The fields
     * @since 2.3.0
     */
    default <T extends Element> Optional<T> getEnclosedElement(@NonNull ElementQuery<T> query) {
        List<T> enclosedElements = getEnclosedElements(query);
        if (!enclosedElements.isEmpty()) {
            return Optional.of(enclosedElements.iterator().next());
        }
        return Optional.empty();
    }

    /**
     * @return Whether the class element is an interface
     */
    default boolean isInterface() {
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
    default @NonNull Map<String, ClassElement> getTypeArguments(@NonNull String type) {
        return Collections.emptyMap();
    }

    /**
     * Get the type arguments for the given type name.
     *
     * @param type The type to retrieve type arguments for
     * @return The type arguments for this class element
     */
    default @NonNull Map<String, ClassElement> getTypeArguments(@NonNull Class<?> type) {
        ArgumentUtils.requireNonNull("type", type);
        return getTypeArguments(type.getName());
    }

    /**
     * @return The type arguments for this class element
     */
    default @NonNull Map<String, ClassElement> getTypeArguments() {
        return Collections.emptyMap();
    }

    /**
     * Builds a map of all the type parameters for a class, its super classes and interfaces.
     * The resulting map contains the name of the class to the a map of the resolved generic types.
     *
     * @return The type arguments for this class element
     */
    default @NonNull Map<String, Map<String, ClassElement>> getAllTypeArguments() {
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

    /**
     * Convert the class element to an element for the same type, but representing an array.
     * Do not mutate the existing instance. Create a new instance instead.
     *
     * @return A new class element
     */
    ClassElement toArray();

    /**
     * Dereference a class element denoting an array type by converting it to its element type.
     * Do not mutate the existing instance. Create a new instance instead.
     *
     * @return A new class element
     * @throws IllegalStateException if this class element doesn't denote an array type
     */
    ClassElement fromArray();

    /**
     * Create a class element for the given simple type.
     * @param type The type
     * @return The class element
     */
    static ClassElement of(Class<?> type) {
        return new ReflectClassElement(
                Objects.requireNonNull(type, "Type cannot be null")
        );
    }

    /**
     * Create a class element for the given simple type.
     * @param typeName The type
     * @return The class element
     */
    @Internal
    static ClassElement of(String typeName) {
        return new ClassElement() {
            @Override
            public boolean isAssignable(String type) {
                return false;
            }

            @Override
            public boolean isAssignable(ClassElement type) {
                return false;
            }

            @Override
            public ClassElement toArray() {
                throw new UnsupportedOperationException("Cannot convert class elements produced by name to an array");
            }

            @Override
            public ClassElement fromArray() {
                throw new UnsupportedOperationException("Cannot convert class elements produced by from an array");
            }

            @NotNull
            @Override
            public String getName() {
                return typeName;
            }

            @Override
            public boolean isPackagePrivate() {
                return false;
            }

            @Override
            public boolean isProtected() {
                return false;
            }

            @Override
            public boolean isPublic() {
                return false;
            }

            @NotNull
            @Override
            public Object getNativeType() {
                return typeName;
            }
        };
    }
}
