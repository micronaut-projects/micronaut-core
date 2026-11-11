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
package io.micronaut.core.type;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Objects;
import java.util.Set;

/**
 * The conversion behind {@link Argument#ofExact(Type)}: a {@link Type} read into the {@link Argument} that keeps
 * every shape of it, through the accessors of the type alone. A type handed in already carries its arguments,
 * bounds and components, so nothing here loads a class, reads a member or asks a class for the variables it
 * declares.
 *
 * @since 5.3.0
 */
@Internal
final class ExactTypes {

    private ExactTypes() {
    }

    /**
     * Convert a type to an argument that keeps every shape of it.
     *
     * @param name The name of the argument, or {@code null}
     * @param type The type
     * @return The argument
     */
    static Argument<?> toArgument(@Nullable String name, Type type) {
        Objects.requireNonNull(type, "Type cannot be null");
        return toArgument(name, type, Set.of());
    }

    /**
     * @param name      The name of the returned argument, or {@code null}
     * @param type      The type to convert
     * @param resolving The type variables whose bounds are being converted
     * @return The converted argument
     */
    private static Argument<?> toArgument(@Nullable String name, Type type, Set<TypeVariable<?>> resolving) {
        if (type instanceof Argument<?> argument) {
            return name == null || name.equals(argument.getName())
                ? argument
                : rebuild(argument, name, argument.getAnnotationMetadata(), argument.getTypeParameters());
        } else if (type instanceof ParameterizedType parameterized) {
            // the type arguments are not named after the variables of the raw type, which only the class could tell
            return Argument.of(rawTypeOf(parameterized.getRawType()), name,
                toArguments(parameterized.getActualTypeArguments(), resolving));
        } else if (type instanceof GenericArrayType array) {
            return arrayOf(toArgument(null, array.getGenericComponentType(), resolving), name);
        } else if (type instanceof WildcardType wildcard) {
            return wildcardOf(name,
                toArguments(wildcard.getUpperBounds(), resolving),
                toArguments(wildcard.getLowerBounds(), resolving));
        } else if (type instanceof Class<?> aClass) {
            // a class carries no type arguments of its own: the variables a generic class declares are read from
            // the class, which this conversion does not do, so a raw List is the argument of List
            return Argument.of(aClass, name);
        } else if (type instanceof TypeVariable<?> variable) {
            return variableOf(name, variable, resolving);
        }
        throw new IllegalArgumentException("Unsupported type " + type.getClass().getName());
    }

    private static Argument<?> variableOf(@Nullable String name, TypeVariable<?> variable, Set<TypeVariable<?>> resolving) {
        if (resolving.contains(variable)) {
            // a bound naming the variable it bounds - `T extends Comparable<T>` - would be converted for ever:
            // inside its own bound the variable stands for the erasure of that bound
            return Argument.ofTypeVariable(rawTypeOf(variable), name, variable.getName(), null, Argument.ZERO_ARGUMENTS, null);
        }
        Set<TypeVariable<?>> nested = CollectionUtils.newHashSet(resolving.size() + 1);
        nested.addAll(resolving);
        nested.add(variable);
        // an unresolved variable is a placeholder of its bound, as the processors generate it; a variable with no
        // bound reported is bounded by Object
        Type[] declaredBounds = variable.getBounds();
        Argument<?>[] bounds = declaredBounds == null || declaredBounds.length == 0
            ? new Argument[]{Argument.OBJECT_ARGUMENT}
            : toArguments(declaredBounds, nested);
        Argument<?> bound = bounds[0];
        return Argument.ofTypeVariable(bound.getType(), name, variable.getName(),
            bound.getAnnotationMetadata(), bound.getTypeParameters(), bounds);
    }

    private static Argument<?>[] toArguments(Type[] types, Set<TypeVariable<?>> resolving) {
        Argument<?>[] arguments = new Argument[types.length];
        for (int i = 0; i < types.length; i++) {
            arguments[i] = toArgument(null, types[i], resolving);
        }
        return arguments;
    }

    /**
     * The array of a component, named as the array is: the type arguments of the component are the ones of the
     * array - a {@code List<String>[]} is a {@code List[]} of {@code E -> String}, as the processors write it - an
     * array of a raw component is raw the same way, and an array of a type variable is a placeholder of that
     * variable whose type is the array.
     */
    private static Argument<?> arrayOf(Argument<?> component, @Nullable String name) {
        Argument<?> array = component.arrayType();
        return rebuild(array, name, component.getAnnotationMetadata(), array.getTypeParameters());
    }

    /**
     * A wildcard with its bounds, as {@link Argument#ofWildcard} and the processors build it: the argument is of
     * the type the wildcard is bounded by, the lower bound first - {@code ? super Book} is {@code Book} - and only
     * an unbounded wildcard is {@code Object}; the bounds are kept alongside, the way {@link WildcardType} reports
     * them.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Argument<?> wildcardOf(@Nullable String name, Argument<?>[] upperBounds, Argument<?>[] lowerBounds) {
        Argument<?> bound;
        if (lowerBounds.length > 0) {
            bound = lowerBounds[0];
        } else if (upperBounds.length > 0) {
            bound = upperBounds[0];
        } else {
            bound = Argument.OBJECT_ARGUMENT;
        }
        return Argument.ofWildcard((Class) bound.getType(), name, bound.getAnnotationMetadata(),
            bound.getTypeParameters(), upperBounds, lowerBounds);
    }

    /**
     * The argument rebuilt with another name, metadata and type parameters, keeping what kind of argument it is.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Argument<?> rebuild(Argument<?> argument, @Nullable String name, AnnotationMetadata metadata, Argument<?>[] typeParameters) {
        if (argument instanceof WildcardArgument<?> wildcard) {
            return Argument.ofWildcard((Class) argument.getType(), name, metadata, typeParameters,
                wildcard.getUpperBounds().toArray(Argument[]::new), wildcard.getLowerBounds().toArray(Argument[]::new));
        }
        if (argument instanceof GenericPlaceholder<?> placeholder) {
            Argument<?>[] bounds = placeholder.getBounds().toArray(Argument[]::new);
            return placeholder.isResolved()
                ? Argument.ofResolvedTypeVariable((Class) argument.getType(), name, placeholder.getVariableName(), metadata, typeParameters, bounds)
                : Argument.ofTypeVariable((Class) argument.getType(), name, placeholder.getVariableName(), metadata, typeParameters, bounds);
        }
        if (argument.isRawType()) {
            return Argument.ofRawType((Class) argument.getType(), name, metadata, typeParameters);
        }
        return Argument.of((Class) argument.getType(), name, metadata, typeParameters);
    }

    /**
     * The class a type erases to: the first bound of a variable or of a wildcard, the lower one first for a
     * wildcard, all the way up.
     */
    private static Class<?> rawTypeOf(Type type) {
        if (type instanceof Class<?> aClass) {
            return aClass;
        } else if (type instanceof Argument<?> argument) {
            return argument.getType();
        } else if (type instanceof ParameterizedType parameterized) {
            return rawTypeOf(parameterized.getRawType());
        } else if (type instanceof TypeVariable<?> variable) {
            Type[] bounds = variable.getBounds();
            return bounds == null || bounds.length == 0 ? Object.class : rawTypeOf(bounds[0]);
        } else if (type instanceof WildcardType wildcard) {
            Type[] lowerBounds = wildcard.getLowerBounds();
            Type[] bounds = lowerBounds.length == 0 ? wildcard.getUpperBounds() : lowerBounds;
            return bounds.length == 0 ? Object.class : rawTypeOf(bounds[0]);
        } else if (type instanceof GenericArrayType array) {
            return rawTypeOf(array.getGenericComponentType()).arrayType();
        }
        throw new IllegalArgumentException("Unsupported type " + type.getClass().getName());
    }
}
