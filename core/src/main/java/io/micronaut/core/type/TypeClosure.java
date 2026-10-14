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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the type closure of a parameterization of a {@link TypeHierarchy}, see
 * {@link TypeHierarchy#getTypeClosure(Argument)}: the recorded super types with the actual type arguments
 * substituted, which needs no walk of its own.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class TypeClosure {

    private TypeClosure() {
    }

    static List<Argument<?>> of(TypeHierarchy hierarchy, Argument<?> parameterization) {
        if (parameterization.getType() != hierarchy.getType()) {
            throw new IllegalArgumentException("The parameterization " + parameterization.getTypeString(false)
                + " is not of the type " + hierarchy.getType().getName());
        }
        List<Argument<?>> superTypes = hierarchy.getSuperTypes();
        List<Argument<?>> closure = new ArrayList<>(superTypes.size() + 1);
        closure.add(parameterization);
        List<Argument<?>> variables = hierarchy.getTypeVariables();
        Argument<?>[] actualArguments = parameterization.getTypeParameters();
        boolean raw = !variables.isEmpty() && (parameterization.isRawType() || actualArguments.length != variables.size());
        if (raw) {
            for (Argument<?> superType : superTypes) {
                closure.add(erase(superType));
            }
            return closure;
        }
        Map<String, Argument<?>> declared = new HashMap<>();
        Map<String, Argument<?>> substitutions = new HashMap<>();
        for (int i = 0; i < variables.size(); i++) {
            String name = variableName(variables.get(i));
            declared.put(name, variables.get(i));
            substitutions.put(name, actualArguments[i]);
        }
        for (Argument<?> superType : superTypes) {
            closure.add(substitute(superType, declared, substitutions));
        }
        return closure;
    }

    /**
     * The erasure of a super type of a raw type: the type without its arguments, marked raw when it declares any.
     */
    private static Argument<?> erase(Argument<?> superType) {
        if (superType.isRawType() || superType.getTypeParameters().length == 0) {
            return superType;
        }
        return Argument.ofRawType(superType.getType(), superType.getName(), superType.getAnnotationMetadata(), superType.getTypeParameters());
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
            // the dimensions the super type adds to the variable: T[] for a T erased to Object is one
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
}
