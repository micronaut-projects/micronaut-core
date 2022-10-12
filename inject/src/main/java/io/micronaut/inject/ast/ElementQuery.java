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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * An interface for querying the AST for elements.
 *
 * @param <T> The element kind
 * @since 2.3.0
 * @author graemerocher
 */
public interface ElementQuery<T extends Element> {

    /**
     * Constant to retrieve inner classes.
     *
     * @since 3.1.0
     */
    ElementQuery<ClassElement> ALL_INNER_CLASSES = ElementQuery.of(ClassElement.class);

    /**
     * Constant to retrieve all fields.
     */
    ElementQuery<FieldElement> ALL_FIELDS = ElementQuery.of(FieldElement.class);

    /**
     * Constant to retrieve all methods.
     */
    ElementQuery<MethodElement> ALL_METHODS = ElementQuery.of(MethodElement.class);

    /**
     * Constant to retrieve instance constructors, not including those of the parent class.
     */
    // static initializers are never returned, so we don't need onlyInstance()
    ElementQuery<ConstructorElement> CONSTRUCTORS = ElementQuery.of(ConstructorElement.class).onlyDeclared();

    /**
     * Indicates that only declared members should be returned and not members from parent classes.
     *
     * @return This query
     */
    @NonNull ElementQuery<T> onlyDeclared();

    /**
     * Search for methods that are injection points.
     * @return This query
     */
    ElementQuery<T> onlyInjected();

    /**
     * Indicates that only concrete members should be returned.
     *
     * @return This query
     */
    @NonNull ElementQuery<T> onlyConcrete();

    /**
     * Indicates that only abstract members should be returned.
     *
     * @return This query
     */
    @NonNull ElementQuery<T> onlyAbstract();

    /**
     * Indicates that only accessible members should be returned. Inaccessible members include:
     *
     * <ul>
     *     <li>package/private members that are in a different package</li>
     *     <li>private members</li>
     *     <li>synthetic members or those whose names start with the dollar symbol</li>
     * </ul>
     *
     * @return This query
     */
    @NonNull ElementQuery<T> onlyAccessible();

    /**
     * Indicates that only accessible members from the given type should be returned. Inaccessible members include:
     *
     * <ul>
     *     <li>package/private members that are in a different package</li>
     *     <li>private members</li>
     *     <li>synthetic members or those whose names start with the dollar symbol</li>
     * </ul>
     *
     * @param fromType The origin type
     * @return This query
     */
    @NonNull ElementQuery<T> onlyAccessible(ClassElement fromType);

    /**
     * Indicates to return only instance (non-static methods).
     * @return The query
     */
    ElementQuery<T> onlyInstance();

    /**
     * Indicates to return only static methods/fields.
     * @return The query
     * @since 4.0.0
     */
    ElementQuery<T> onlyStatic();

    /**
     * Indicates to exclude any property elements (read write methods and a field).
     * @return The query
     * @since 4.0.0
     */
    @Experimental
    ElementQuery<T> excludePropertyElements();

    /**
     * Indicates to include enum constants, only applicable for fields query.
     * @since 3.4.0
     * @return The query
     */
    ElementQuery<T> includeEnumConstants();

    /**
     * Indicates to include overridden methods, only applicable for methods query.
     * @since 3.4.0
     * @return The query
     */
    ElementQuery<T> includeOverriddenMethods();

    /**
     * Indicates to include hidden methods/fields, only applicable for methods/fields query.
     * @since 3.4.0
     * @return The query
     */
    ElementQuery<T> includeHiddenElements();

    /**
     * Allows filtering elements by name.
     * @param predicate The predicate to use. Should return true to include the element.
     * @return This query
     */
    @NonNull ElementQuery<T> named(@NonNull Predicate<String> predicate);

    /**
     * Allows filtering elements by name.
     * @param name The name to filter by
     * @return This query
     * @since 3.5.2
     */
    default @NonNull ElementQuery<T> named(@NonNull String name) {
        return named(n -> n.equals(name));
    }

    /**
     * Allows filtering elements by type. For {@link MethodElement} instances this is based on the return type.
     * @param predicate The predicate to use. Should return true to include the element.
     * @return This query
     */
    @NonNull ElementQuery<T> typed(@NonNull Predicate<ClassElement> predicate);

    /**
     * Allows filtering elements by annotation.
     * @param predicate The predicate to use. Should return true to include the element.
     * @return This query
     */
    @NonNull ElementQuery<T> annotated(@NonNull Predicate<AnnotationMetadata> predicate);

    /**
     * Allows filtering by modifiers.
     * @param predicate The predicate to use. Should return true to include the element.
     * @return This query
     */
    @NonNull ElementQuery<T> modifiers(@NonNull Predicate<Set<ElementModifier>> predicate);

    /**
     * A final filter that allows access to the materialized Element. This method should be used
     * as a last resort as it is less efficient than the other filtration methods.
     * @param predicate The predicate to use. Should return true to include the element.
     * @return This query
     */
    @NonNull ElementQuery<T> filter(@NonNull Predicate<T> predicate);

    /**
     * Build the query result.
     *
     * @return The query result.
     */
    @NonNull Result<T> result();

    /**
     * Create a new {@link ElementQuery} for the given element type.
     * @param elementType The element type
     * @param <T1> The element generic type
     * @return The query
     */
    static @NonNull <T1 extends Element> ElementQuery<T1> of(@NonNull Class<T1> elementType) {
        return new DefaultElementQuery<>(
                Objects.requireNonNull(elementType, "Element type cannot be null")
        );
    }

    /**
     * Result interface when building a query.
     * @param <T> The element type.
     */
    interface Result<T extends Element> {

        /**
         * @return Whether to return only abstract methods
         */
        boolean isOnlyAbstract();

        /**
         * @return Whether to return only injection points
         */
        boolean isOnlyInjected();

        /**
         * @return Whether to return only concrete methods
         */
        boolean isOnlyConcrete();

        /**
         * @return The element type
         */
        @NonNull Class<T> getElementType();

        /**
         * @return Whether to return only accessible members
         */
        boolean isOnlyAccessible();

        /**
         * @return Get the type this element is only accessible from.
         */
        Optional<ClassElement> getOnlyAccessibleFromType();

        /**
         * @return Whether to declare only declared members
         */
        boolean isOnlyDeclared();

        /**
         * @return Whether to return only instance methods
         */
        boolean isOnlyInstance();

        /**
         * @return Whether to return only static methods / fields
         * @since 4.0.0
         */
        boolean isOnlyStatic();

        /**
         * @return Whether to include enum constants
         * @since 3.4.0
         */
        boolean isIncludeEnumConstants();

        /**
         * @return Whether to include overridden methods
         * @since 3.4.0
         */
        boolean isIncludeOverriddenMethods();

        /**
         * @return Whether to include hidden methods/fields
         * @since 3.4.0
         */
        boolean isIncludeHiddenElements();

        /**
         * @return Whether to exclude property elements
         * @since 4.0.0
         */
        boolean isExcludePropertyElements();

        /**
         * @return The name predicates
         */
        @NonNull List<Predicate<String>> getNamePredicates();


        /**
         * @return The name predicates
         * @since 3.0.0
         */
        @NonNull List<Predicate<ClassElement>> getTypePredicates();

        /**
         * @return The annotation predicates
         */
        @NonNull List<Predicate<AnnotationMetadata>> getAnnotationPredicates();

        /**
         * @return The modifier predicate
         */
        @NonNull List<Predicate<Set<ElementModifier>>> getModifierPredicates();

        /**
         * @return The element predicates
         */
        @NonNull List<Predicate<T>> getElementPredicates();
    }
}
