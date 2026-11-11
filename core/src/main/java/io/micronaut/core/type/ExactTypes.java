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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Objects;
import java.util.Set;

/**
 * The conversion behind {@link Argument#ofExact(Type)}: a {@link Type} read into the {@link Argument} that keeps
 * every shape of it, through the accessors of the {@code Type} interfaces alone. This class loads no class,
 * reads no member and asks no class for the variables it declares. The type handed in answers the accessors
 * itself: the JDK's own implementations of {@link TypeVariable} and {@link WildcardType} may resolve their
 * bounds lazily, from the generic signature, the first time they are asked.
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
     * @param type The type
     * @return The argument
     */
    static Argument<?> toArgument(Type type) {
        Objects.requireNonNull(type, "Type cannot be null");
        return toArgument(type, Set.of());
    }

    /**
     * @param type      The type to convert
     * @param resolving The type variables whose bounds are being converted
     * @return The converted argument
     */
    private static Argument<?> toArgument(Type type, Set<TypeVariable<?>> resolving) {
        if (type instanceof Argument<?> argument) {
            return argument;
        } else if (type instanceof ParameterizedType parameterized) {
            // the type arguments are not named after the variables of the raw type, which only the class could tell
            return Argument.of(rawTypeOf(parameterized.getRawType()), (String) null,
                toArguments(parameterized.getActualTypeArguments(), resolving));
        } else if (type instanceof GenericArrayType array) {
            // the type arguments of the component are the ones of the array - a List<String>[] is a List[] of
            // String, as the processors write it - and an array of a variable is a placeholder of the variable
            return toArgument(array.getGenericComponentType(), resolving).arrayType();
        } else if (type instanceof WildcardType wildcard) {
            return wildcardOf(toArguments(wildcard.getUpperBounds(), resolving), toArguments(wildcard.getLowerBounds(), resolving));
        } else if (type instanceof Class<?> aClass) {
            // a class carries no type arguments of its own: the variables a generic class declares are read from
            // the class, which this conversion does not do, so a raw List is the argument of List
            return Argument.of(aClass);
        } else if (type instanceof TypeVariable<?> variable) {
            return variableOf(variable, resolving);
        }
        throw new IllegalArgumentException("Unsupported type " + type.getClass().getName());
    }

    private static Argument<?> variableOf(TypeVariable<?> variable, Set<TypeVariable<?>> resolving) {
        if (resolving.contains(variable)) {
            // a bound naming the variable it bounds - `T extends Comparable<T>` - would be converted for ever:
            // inside its own bound the variable stands for the erasure of that bound
            return Argument.ofTypeVariable(rawTypeOf(variable), null, variable.getName(), null, Argument.ZERO_ARGUMENTS, null);
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
        return Argument.ofTypeVariable(bound.getType(), null, variable.getName(),
            bound.getAnnotationMetadata(), bound.getTypeParameters(), bounds);
    }

    private static Argument<?>[] toArguments(Type[] types, Set<TypeVariable<?>> resolving) {
        Argument<?>[] arguments = new Argument[types.length];
        for (int i = 0; i < types.length; i++) {
            arguments[i] = toArgument(types[i], resolving);
        }
        return arguments;
    }

    /**
     * A wildcard with its bounds, as {@link Argument#ofWildcard} and the processors build it: the argument is of
     * the type the wildcard is bounded by, the lower bound first - {@code ? super Book} is {@code Book} - and only
     * an unbounded wildcard is {@code Object}; the bounds are kept alongside, the way {@link WildcardType} reports
     * them.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Argument<?> wildcardOf(Argument<?>[] upperBounds, Argument<?>[] lowerBounds) {
        Argument<?> bound;
        if (lowerBounds.length > 0) {
            bound = lowerBounds[0];
        } else if (upperBounds.length > 0) {
            bound = upperBounds[0];
        } else {
            bound = Argument.OBJECT_ARGUMENT;
        }
        return Argument.ofWildcard((Class) bound.getType(), null, bound.getAnnotationMetadata(),
            bound.getTypeParameters(), upperBounds, lowerBounds);
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
