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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Computes the type closure of a parameterization of a {@link TypeHierarchy}, see
 * {@link TypeHierarchy#getTypeClosure(Argument, Function)}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class TypeClosure {

    // class hierarchies are acyclic; the cap only guards against a resolver that answers with the wrong hierarchy
    private static final int MAX_DEPTH = 64;

    private TypeClosure() {
    }

    static List<Argument<?>> of(TypeHierarchy hierarchy, Argument<?> parameterization, Function<Class<?>, Optional<TypeHierarchy>> hierarchies) {
        if (parameterization.getType() != hierarchy.getType()) {
            throw new IllegalArgumentException("The parameterization " + parameterization.getTypeString(false)
                + " is not of the type " + hierarchy.getType().getName());
        }
        List<Argument<?>> closure = new ArrayList<>();
        Set<Shape> seen = new HashSet<>();
        collect(hierarchy, parameterization, hierarchies, closure, seen, 0);
        closure.removeIf(argument -> argument.getType() == Object.class);
        closure.add(Argument.OBJECT_ARGUMENT);
        return closure;
    }

    private static void collect(TypeHierarchy hierarchy,
                                Argument<?> parameterization,
                                Function<Class<?>, Optional<TypeHierarchy>> hierarchies,
                                List<Argument<?>> closure,
                                Set<Shape> seen,
                                int depth) {
        if (!seen.add(new Shape(parameterization))) {
            return;
        }
        closure.add(parameterization);
        List<Argument<?>> variables = hierarchy.getTypeVariables();
        Argument<?>[] actualArguments = parameterization.getTypeParameters();
        boolean raw = !variables.isEmpty() && (parameterization.isRawType() || actualArguments.length != variables.size());
        Map<String, Argument<?>> substitutions = new HashMap<>();
        Map<String, Argument<?>> declared = new HashMap<>();
        if (!raw) {
            for (int i = 0; i < variables.size(); i++) {
                String name = variableName(variables.get(i));
                declared.put(name, variables.get(i));
                substitutions.put(name, actualArguments[i]);
            }
        }
        hierarchy.getGenericSuperclass().ifPresent(superclass ->
            supertype(superclass, raw, declared, substitutions, hierarchies, closure, seen, depth));
        for (Argument<?> anInterface : hierarchy.getGenericInterfaces()) {
            supertype(anInterface, raw, declared, substitutions, hierarchies, closure, seen, depth);
        }
    }

    private static void supertype(Argument<?> supertype,
                                  boolean raw,
                                  Map<String, Argument<?>> declared,
                                  Map<String, Argument<?>> substitutions,
                                  Function<Class<?>, Optional<TypeHierarchy>> hierarchies,
                                  List<Argument<?>> closure,
                                  Set<Shape> seen,
                                  int depth) {
        Argument<?> actual = raw ? erase(supertype) : substitute(supertype, declared, substitutions);
        Optional<TypeHierarchy> hierarchy = depth < MAX_DEPTH ? hierarchies.apply(actual.getType()) : Optional.empty();
        if (hierarchy.isPresent() && hierarchy.get().getType() == actual.getType()) {
            collect(hierarchy.get(), actual, hierarchies, closure, seen, depth + 1);
        } else if (seen.add(new Shape(actual))) {
            closure.add(actual);
        }
    }

    /**
     * The erasure of a supertype of a raw type: the type without its arguments, marked raw when it declares any.
     */
    private static Argument<?> erase(Argument<?> supertype) {
        if (supertype.isRawType() || supertype.getTypeParameters().length == 0) {
            return supertype;
        }
        return Argument.ofRawType(supertype.getType(), supertype.getName(), supertype.getAnnotationMetadata(), supertype.getTypeParameters());
    }

    private static Argument<?> substitute(Argument<?> argument, Map<String, Argument<?>> declared, Map<String, Argument<?>> substitutions) {
        if (argument instanceof WildcardArgument<?> wildcard) {
            Argument<?>[] upperBounds = substitute(wildcard.getUpperBounds(), declared, substitutions);
            Argument<?>[] lowerBounds = substitute(wildcard.getLowerBounds(), declared, substitutions);
            Class<?> erasure = upperBounds.length == 0 ? argument.getType() : upperBounds[0].getType();
            return Argument.ofWildcard(erasure, argument.getName(), argument.getAnnotationMetadata(), null, upperBounds, lowerBounds);
        }
        if (argument instanceof GenericPlaceholder<?> placeholder && !placeholder.isResolved()) {
            String name = placeholder.getVariableName();
            Argument<?> actual = substitutions.get(name);
            Argument<?> variable = declared.get(name);
            if (actual == null || variable == null) {
                // a variable of another declaration: kept as the hierarchy wrote it
                return argument;
            }
            // the dimensions the supertype adds to the variable: T[] for a T erased to Object is one
            int dimensions = dimensions(argument.getType()) - dimensions(variable.getType());
            return arrayOf(actual, dimensions);
        }
        Argument<?>[] typeParameters = argument.getTypeParameters();
        if (typeParameters.length == 0 || argument.isRawType()) {
            return argument;
        }
        return argument.withTypeParameters(substitute(List.of(typeParameters), declared, substitutions));
    }

    private static Argument<?>[] substitute(List<Argument<?>> arguments, Map<String, Argument<?>> declared, Map<String, Argument<?>> substitutions) {
        Argument<?>[] substituted = new Argument<?>[arguments.size()];
        for (int i = 0; i < substituted.length; i++) {
            substituted[i] = substitute(arguments.get(i), declared, substitutions);
        }
        return substituted;
    }

    private static Argument<?> arrayOf(Argument<?> argument, int dimensions) {
        Argument<?> array = argument;
        for (int i = 0; i < dimensions; i++) {
            if (array instanceof WildcardArgument<?> wildcard) {
                // there is no array of a wildcard: an array of the bounds is the nearest, Object[] for an unbounded one
                Argument<?>[] upperBounds = arraysOf(wildcard.getUpperBounds());
                Argument<?>[] lowerBounds = arraysOf(wildcard.getLowerBounds());
                Class<?> erasure = upperBounds.length == 0 ? array.getType().arrayType() : upperBounds[0].getType();
                array = Argument.ofWildcard(erasure, array.getName(), array.getAnnotationMetadata(), null, upperBounds, lowerBounds);
            } else {
                array = array.arrayType();
            }
        }
        return array;
    }

    private static Argument<?>[] arraysOf(List<Argument<?>> bounds) {
        Argument<?>[] arrays = new Argument<?>[bounds.size()];
        for (int i = 0; i < arrays.length; i++) {
            arrays[i] = bounds.get(i).arrayType();
        }
        return arrays;
    }

    private static int dimensions(Class<?> type) {
        int dimensions = 0;
        for (Class<?> current = type; current.isArray(); current = current.getComponentType()) {
            dimensions++;
        }
        return dimensions;
    }

    private static String variableName(Argument<?> variable) {
        return variable instanceof GenericPlaceholder<?> placeholder ? placeholder.getVariableName() : variable.getName();
    }

    /**
     * An argument compared by its structure, so that two paths to the same parameterized supertype meet.
     *
     * @param argument The argument
     */
    private record Shape(Argument<?> argument) {

        @Override
        public boolean equals(@Nullable Object o) {
            return o instanceof Shape other && argument.equalsStructure(other.argument);
        }

        @Override
        public int hashCode() {
            return argument.structureHashCode();
        }
    }
}
