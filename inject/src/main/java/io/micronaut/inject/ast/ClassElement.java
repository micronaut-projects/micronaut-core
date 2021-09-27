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
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.beans.BeanElementBuilder;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.micronaut.inject.writer.BeanDefinitionVisitor.PROXY_SUFFIX;

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
     * In this case of calling {@link #getTypeArguments()} a returned {@link ClassElement} may represent a type variable
     * in which case this method will return {@code true}.
     *
     * @return Is this type a type variable.
     * @since 3.0.0
     */
    default boolean isTypeVariable() {
        return false;
    }

    /**
     * @see FreeTypeVariableElement
     * @return Whether this is a free type variable.
     */
    @Experimental
    default boolean isFreeTypeVariable() {
        return this instanceof FreeTypeVariableElement;
    }

    /**
     * @see WildcardElement
     * @return Whether this is a wildcard.
     */
    @Experimental
    default boolean isWildcard() {
        return this instanceof WildcardElement;
    }

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
     * @return True if the class represents a proxy
     */
    default boolean isProxy() {
        return getSimpleName().endsWith(PROXY_SUFFIX);
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

    /**
     * @return The interfaces implemented by this class element
     */
    default Collection<ClassElement> getInterfaces() {
        return Collections.emptyList();
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
     * The package name.
     *
     * @return The package name
     * @since 3.0.0
     */
    default PackageElement getPackage() {
        return PackageElement.of(getPackageName());
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
     * Returns the enclosing type if {@link #isInner()} return {@code true}.
     *
     * @return The enclosing type if any
     * @since 3.0.0
     */
    default Optional<ClassElement> getEnclosingType() {
        return Optional.empty();
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
     * The list of type arguments bound to this type, or an empty list if there are no type arguments or this is a raw
     * type.
     * <p>
     * Note that for compatibility reasons, this method is inconsistent with {@link #getTypeArguments()}. In particular,
     * this method reflects the <i>declaration</i> type: If there is a {@code class Test<T> { T field; }}, this method
     * will return {@code T} as the field type, even if the field type was obtained through a {@code Test<String>}.
     *
     * @return The list of type arguments, in the same order as {@link #getDeclaredTypeVariables()}. Must be empty or
     * of the same length as {@link #getDeclaredTypeVariables()}.
     */
    @NonNull
    @Experimental
    default List<? extends ClassElement> getBoundTypeArguments() {
        return new ArrayList<>(getTypeArguments().values());
    }

    /**
     * The type arguments declared on the raw class. Independent of the actual
     * {@link #getBoundTypeArguments() bound type arguments}.
     *
     * @return The type arguments declared on this class.
     */
    @NonNull
    @Experimental
    default List<? extends FreeTypeVariableElement> getDeclaredTypeVariables() {
        return Collections.emptyList();
    }

    /**
     * Get a {@link ClassElement} instance corresponding to this type, but without any type arguments bound. For
     * {@code List<String>}, this returns {@code List}.
     *
     * @return The raw class of this potentially parameterized type.
     */
    @NonNull
    @Experimental
    default ClassElement getRawClass() {
        return withBoundTypeArguments(Collections.emptyList());
    }

    /**
     * Get a {@link ClassElement} instance corresponding to this type, but with the given type arguments. This is best
     * effort – implementations may only support {@link ClassElement}s that come from the same visitor context, and
     * other {@link ClassElement}s only to a limited degree.
     *
     * @param typeArguments The new type arguments.
     * @return A {@link ClassElement} of the same raw class with the new type arguments.
     * @throws UnsupportedOperationException If any of the given type arguments are unsupported.
     */
    @NonNull
    @Experimental
    default ClassElement withBoundTypeArguments(@NonNull List<? extends ClassElement> typeArguments) {
        return this;
    }

    /**
     * Perform a fold operation on all this type's component types (type arguments, wildcard bounds), and then on this
     * type. For {@code List<? extends String>}, this returns {@code f(List<f(? extends f(String))>)}. The bounds of
     * type variables are not folded.
     * <p>
     * {@code null} has special meaning here. Returning {@code null} from a fold operation will try to make the
     * surrounding type a raw type. For example, for {@code Map<String, Object>}, returning {@code null} for the fold
     * on {@code Object} will lead to the parameterized {@code Map<String, null>} type being replaced by {@code Map}.
     * <p>
     * This also means that this method may return {@code null} if the top-level fold operation returned {@code null}.
     *
     * @param fold The fold operation to apply recursively to all component types.
     * @return The folded type.
     */
    @Experimental
    default ClassElement foldTypes(@NonNull Function<ClassElement, ClassElement> fold) {
        return fold.apply(this);
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
    @NonNull ClassElement toArray();

    /**
     * Dereference a class element denoting an array type by converting it to its element type.
     * Do not mutate the existing instance. Create a new instance instead.
     *
     * @return A new class element
     * @throws IllegalStateException if this class element doesn't denote an array type
     */
    @NonNull ClassElement fromArray();

    /**
     * This method adds an associated bean using this class element as the originating element.
     *
     * <p>Note that this method can only be called on classes being directly compiled by Micronaut. If the ClassElement is
     * loaded from pre-compiled code an {@link UnsupportedOperationException} will be thrown.</p>
     * @param type The type of the bean
     * @return A bean builder
     */
    default @NonNull
    BeanElementBuilder addAssociatedBean(@NonNull ClassElement type) {
        throw new UnsupportedOperationException("Element of type [" + getClass() + "] does not support adding associated beans at compilation time");
    }

    /**
     * Create a class element for the given simple type.
     * @param type The type
     * @return The class element
     */
    static @NonNull ClassElement of(@NonNull Class<?> type) {
        return new ReflectClassElement(
                Objects.requireNonNull(type, "Type cannot be null")
        );
    }

    /**
     * Create a class element for the given complex type.
     *
     * @param type The type
     * @return The class element
     */
    @Experimental
    @NonNull
    static ClassElement of(@NonNull Type type) {
        Objects.requireNonNull(type, "Type cannot be null");
        if (type instanceof Class) {
            return new ReflectClassElement((Class<?>) type);
        } else if (type instanceof TypeVariable<?>) {
            return new ReflectFreeTypeVariableElement((TypeVariable<?>) type, 0);
        } else if (type instanceof WildcardType) {
            return new ReflectWildcardElement((WildcardType) type);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pType = (ParameterizedType) type;
            if (pType.getOwnerType() != null) {
                throw new UnsupportedOperationException("Owner types are not supported");
            }
            return new ReflectClassElement(ReflectTypeElement.getErasure(type)) {
                @NonNull
                @Override
                public List<? extends ClassElement> getBoundTypeArguments() {
                    return Arrays.stream(pType.getActualTypeArguments())
                            .map(ClassElement::of)
                            .collect(Collectors.toList());
                }
            };
        } else if (type instanceof GenericArrayType) {
            return of(((GenericArrayType) type).getGenericComponentType()).toArray();
        } else {
            throw new IllegalArgumentException("Bad type: " + type.getClass().getName());
        }
    }

    /**
     * Create a class element for the given simple type.
     * @param type The type
     * @param annotationMetadata The annotation metadata
     * @param typeArguments The type arguments
     * @return The class element
     * @since 2.4.0
     */
    static @NonNull ClassElement of(
            @NonNull Class<?> type,
            @NonNull AnnotationMetadata annotationMetadata,
            @NonNull Map<String, ClassElement> typeArguments) {
        Objects.requireNonNull(annotationMetadata, "Annotation metadata cannot be null");
        Objects.requireNonNull(typeArguments, "Type arguments cannot be null");
        return new ReflectClassElement(
                Objects.requireNonNull(type, "Type cannot be null")
        ) {
            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                return annotationMetadata;
            }

            @Override
            public Map<String, ClassElement> getTypeArguments() {
                return Collections.unmodifiableMap(typeArguments);
            }

            @NonNull
            @Override
            public List<? extends ClassElement> getBoundTypeArguments() {
                return getDeclaredTypeVariables().stream()
                        .map(tv -> typeArguments.get(tv.getVariableName()))
                        .collect(Collectors.toList());
            }
        };
    }

    /**
     * Create a class element for the given simple type.
     * @param typeName The type
     * @return The class element
     */
    @Internal
    static @NonNull ClassElement of(@NonNull String typeName) {
        return new SimpleClassElement(typeName);
    }

    /**
     * Create a class element for the given simple type.
     * @param typeName The type
     * @param isInterface Is the type an interface
     * @param annotationMetadata The annotation metadata
     * @return The class element
     */
    @Internal
    static @NonNull ClassElement of(@NonNull String typeName, boolean isInterface, @Nullable AnnotationMetadata annotationMetadata) {
        return new SimpleClassElement(typeName, isInterface, annotationMetadata);
    }

    /**
     * Create a class element for the given simple type.
     * @param typeName The type
     * @param isInterface Is the type an interface
     * @param annotationMetadata The annotation metadata
     * @param typeArguments The type arguments
     * @return The class element
     */
    @Internal
    static @NonNull ClassElement of(@NonNull String typeName, boolean isInterface, @Nullable AnnotationMetadata annotationMetadata, Map<String, ClassElement> typeArguments) {
        return new SimpleClassElement(typeName, isInterface, annotationMetadata);
    }
}
