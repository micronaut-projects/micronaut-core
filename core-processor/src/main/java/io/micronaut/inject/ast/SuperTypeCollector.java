/*
 * Copyright 2017-2026 original authors
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
import io.micronaut.core.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Collects the super types of a type for {@link ClassElement#getAllSuperTypes()}: one walk of the hierarchy, each
 * super type bound into the arguments of the type the walk starts from, the super types of a raw type erased, and
 * a type reached again by another path neither listed nor walked again.
 *
 * <p>The walk asks a function for the direct super types of each type it reaches, so a language whose types can
 * have several concrete bases, as Python's can, walks all of them where {@link #declared(ClassElement)} walks the
 * one super class and then the interfaces a {@link ClassElement} declares.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class SuperTypeCollector {

    /**
     * The super types every array has besides the arrays of the super types of its component, in the order an
     * array lists them.
     */
    private static final List<Class<?>> ARRAY_SUPER_TYPES = List.of(Object.class, Cloneable.class, Serializable.class);

    private SuperTypeCollector() {
    }

    /**
     * The direct super types a class element declares: its super class, then its interfaces.
     *
     * @param type The type
     * @return The direct super types
     */
    @NonNull
    public static Stream<ClassElement> declared(@NonNull ClassElement type) {
        return Stream.concat(type.getSuperType().stream(), type.getInterfaces().stream());
    }

    /**
     * The type and its super types, as {@link ClassElement#getAllSuperTypes()} lists them.
     *
     * @param type             The type
     * @param directSuperTypes The direct super types of a type reached by the walk, as the language declares them,
     *                         with their arguments written in the variables of that type
     * @return The type first, its super types, and {@code Object} last
     */
    @NonNull
    public static List<ClassElement> collect(@NonNull ClassElement type,
                                             @NonNull Function<ClassElement, Stream<ClassElement>> directSuperTypes) {
        return collect(type, type.getTypeArguments(), directSuperTypes, UnaryOperator.identity());
    }

    /**
     * The type and its super types, as {@link ClassElement#getAllSuperTypes()} lists them, for a language that
     * binds the variables of the type itself, or walks a super type, other than as the element answers.
     *
     * @param type             The type
     * @param bindings         What the variables of the type are bound to, by name: its own arguments, or its
     *                         declared variables for a type whose arguments answer their bounds
     * @param directSuperTypes The direct super types of a type reached by the walk, as the language declares them,
     *                         with their arguments written in the variables of that type
     * @param declaration      The element to ask for the direct super types of a listed super type: the super type
     *                         itself when its super types come back written in its own variables, or its declaration
     *                         when a resolved variable of another language would lose the name they are bound by
     * @return The type first, its super types, and {@code Object} last
     */
    @NonNull
    public static List<ClassElement> collect(@NonNull ClassElement type,
                                             @NonNull Map<String, ClassElement> bindings,
                                             @NonNull Function<ClassElement, Stream<ClassElement>> directSuperTypes,
                                             @NonNull UnaryOperator<ClassElement> declaration) {
        List<ClassElement> result = new ArrayList<>();
        result.add(type);
        if (type.isArray()) {
            // The direct super types of T[] are S[] for each direct super type S of T, then Object, Cloneable and
            // Serializable (JLS 4.10.3). An array of N dimensions of C is so an array of N dimensions of each super
            // type of C and, its component arrays being arrays, an array of k dimensions of Object, Cloneable and
            // Serializable for k from N - 1 down to 1, then those three; a primitive C has no super types of its own
            ClassElement component = type;
            int dimensions = 0;
            while (component.isArray()) {
                component = component.fromArray();
                dimensions++;
            }
            Set<String> seen = new HashSet<>();
            seen.add(key(type));
            if (!component.isPrimitive()) {
                List<ClassElement> componentSuperTypes = collect(component, component.getTypeArguments(), directSuperTypes, declaration);
                for (int i = 1; i < componentSuperTypes.size(); i++) {
                    addOnce(arrayOf(componentSuperTypes.get(i), dimensions), seen, result);
                }
            }
            for (int arrayDimensions = dimensions - 1; arrayDimensions > 0; arrayDimensions--) {
                for (Class<?> arrayType : ARRAY_SUPER_TYPES) {
                    addOnce(arrayOf(ClassElement.of(arrayType), arrayDimensions), seen, result);
                }
            }
            result.add(ClassElement.of(Cloneable.class));
            result.add(ClassElement.of(Serializable.class));
        } else if (type.isPrimitive()) {
            return result;
        } else if (isObject(type)) {
            return result;
        } else {
            Set<String> seen = new HashSet<>();
            seen.add(key(type));
            collect(type, bindings, type.isRawType(), directSuperTypes, declaration, seen, result);
        }
        result.add(ClassElement.of(Object.class));
        return result;
    }

    private static ClassElement arrayOf(ClassElement type, int dimensions) {
        ClassElement array = type;
        for (int i = 0; i < dimensions; i++) {
            array = array.toArray();
        }
        return array;
    }

    private static void addOnce(ClassElement type, Set<String> seen, List<ClassElement> result) {
        if (seen.add(key(type))) {
            result.add(type);
        }
    }

    private static boolean isObject(ClassElement type) {
        return Object.class.getName().equals(type.getName()) && !type.isArray();
    }

    /**
     * Collects the super types of a type into the result, each written in the arguments of the type the walk
     * started from.
     *
     * @param type             The type whose direct super types are collected
     * @param bindings         What the variables of the type are bound to, by name, in the arguments of the first type
     * @param raw              Whether the type is raw, or is reached through a raw type, which erases its super types
     * @param directSuperTypes The direct super types of a type
     * @param declaration      The element to walk for a listed super type
     * @param seen             The keys of the types listed so far
     * @param result           The types listed so far
     */
    private static void collect(ClassElement type,
                                Map<String, ClassElement> bindings,
                                boolean raw,
                                Function<ClassElement, Stream<ClassElement>> directSuperTypes,
                                UnaryOperator<ClassElement> declaration,
                                Set<String> seen,
                                List<ClassElement> result) {
        directSuperTypes.apply(type).forEach(superType -> {
            if (isObject(superType)) {
                return;
            }
            // The arguments of a direct super type are written in the variables of the type, which the bindings
            // resolve, so one binding brings the super type into the arguments of the first type
            Map<String, ClassElement> typeArguments = superType.getTypeArguments();
            Map<String, ClassElement> resolvedArguments = typeArguments;
            ClassElement resolved = superType;
            if (raw) {
                if (!typeArguments.isEmpty()) {
                    resolvedArguments = Collections.emptyMap();
                    resolved = superType.withTypeArguments(resolvedArguments);
                }
            } else {
                resolvedArguments = TypeVariableBinder.bind(typeArguments, bindings);
                if (resolvedArguments != typeArguments) {
                    resolved = superType.withTypeArguments(resolvedArguments);
                }
            }
            if (seen.add(key(resolved))) {
                result.add(resolved);
                collect(declaration.apply(superType), resolvedArguments, raw || superType.isRawType(), directSuperTypes, declaration, seen, result);
            }
        });
    }

    /**
     * The key a super type is listed once by: its name and its type arguments, a raw type by its name alone.
     *
     * @param type The type
     * @return The key
     */
    private static String key(ClassElement type) {
        if (type instanceof GenericPlaceholderElement placeholder) {
            return placeholder.getResolved()
                .map(SuperTypeCollector::key)
                .orElseGet(() -> placeholder.getVariableName() + "[]".repeat(type.getArrayDimensions()));
        }
        if (type instanceof WildcardElement wildcard) {
            StringBuilder key = new StringBuilder("?");
            for (ClassElement bound : wildcard.getUpperBounds()) {
                key.append(" extends ").append(key(bound));
            }
            for (ClassElement bound : wildcard.getLowerBounds()) {
                key.append(" super ").append(key(bound));
            }
            return key.toString();
        }
        StringBuilder key = new StringBuilder(type.getName());
        key.append("[]".repeat(type.getArrayDimensions()));
        if (!type.isRawType() && !type.getTypeArguments().isEmpty()) {
            key.append('<');
            boolean first = true;
            for (ClassElement argument : type.getTypeArguments().values()) {
                if (!first) {
                    key.append(',');
                }
                first = false;
                key.append(key(argument));
            }
            key.append('>');
        }
        return key.toString();
    }
}
