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

import java.util.List;

/**
 * The type an {@link Argument} was written as, read from what the argument carries: the structural comparison
 * behind {@link Argument#equalsStructure(Argument)} and the array conversions behind
 * {@link Argument#componentType()} and {@link Argument#arrayType()}.
 *
 * <p>Type arguments are read from {@link Argument#getTypeParameters()}, by position, and never from the
 * name-keyed {@link Argument#getTypeVariables()}, so none of it depends on the type parameters being named.</p>
 *
 * @since 5.3.0
 */
@Internal
final class ArgumentStructure {

    private static final int WILDCARD_SEED = 0x3f;

    private ArgumentStructure() {
    }

    /**
     * @param argument The argument
     * @return Whether it is a placeholder of a variable left unresolved: the variable itself, or an array of it
     */
    private static boolean isVariableOrArrayOfVariable(Argument<?> argument) {
        return argument instanceof GenericPlaceholder<?> placeholder && !placeholder.isResolved();
    }

    static boolean equals(Argument<?> one, @Nullable Argument<?> other) {
        return equals(one, other, null);
    }

    private static boolean equals(Argument<?> one, @Nullable Argument<?> other, @Nullable Scope scope) {
        if (one == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (one instanceof WildcardArgument<?> wildcard) {
            return other instanceof WildcardArgument<?> otherWildcard
                && equals(wildcard.getUpperBounds(), otherWildcard.getUpperBounds(), scope)
                && equals(wildcard.getLowerBounds(), otherWildcard.getLowerBounds(), scope);
        }
        if (other instanceof WildcardArgument<?> || one.getType() != other.getType()) {
            return false;
        }
        boolean variable = isVariableOrArrayOfVariable(one);
        if (variable != isVariableOrArrayOfVariable(other)) {
            return false;
        }
        if (variable) {
            String name = ((GenericPlaceholder<?>) one).getVariableName();
            if (!name.equals(((GenericPlaceholder<?>) other).getVariableName())) {
                return false;
            }
            // the T within the bounds of T extends Comparable<T> is T itself, whose bounds are being compared
            return Scope.declares(scope, name)
                || equals(((GenericPlaceholder<?>) one).getBounds(), ((GenericPlaceholder<?>) other).getBounds(), new Scope(name, scope));
        }
        boolean typeArguments = one.hasTypeArguments();
        if (typeArguments != other.hasTypeArguments()) {
            return false;
        }
        if (!typeArguments) {
            return true;
        }
        Argument<?>[] oneArguments = one.getTypeParameters();
        Argument<?>[] otherArguments = other.getTypeParameters();
        if (oneArguments.length != otherArguments.length) {
            return false;
        }
        for (int i = 0; i < oneArguments.length; i++) {
            if (!equals(oneArguments[i], otherArguments[i], scope)) {
                return false;
            }
        }
        return true;
    }

    private static boolean equals(List<Argument<?>> one, List<Argument<?>> other, @Nullable Scope scope) {
        int size = one.size();
        if (size != other.size()) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            if (!equals(one.get(i), other.get(i), scope)) {
                return false;
            }
        }
        return true;
    }

    static int hashCode(Argument<?> argument) {
        return hashCode(argument, null);
    }

    private static int hashCode(Argument<?> argument, @Nullable Scope scope) {
        if (argument instanceof WildcardArgument<?> wildcard) {
            return 31 * (31 * WILDCARD_SEED + hashCode(wildcard.getUpperBounds(), scope)) + hashCode(wildcard.getLowerBounds(), scope);
        }
        int hash = argument.getType().hashCode();
        if (isVariableOrArrayOfVariable(argument)) {
            GenericPlaceholder<?> placeholder = (GenericPlaceholder<?>) argument;
            String name = placeholder.getVariableName();
            hash = 31 * hash + name.hashCode();
            return Scope.declares(scope, name) ? hash : 31 * hash + hashCode(placeholder.getBounds(), new Scope(name, scope));
        }
        if (!argument.hasTypeArguments()) {
            return hash;
        }
        for (Argument<?> typeArgument : argument.getTypeParameters()) {
            hash = 31 * hash + hashCode(typeArgument, scope);
        }
        return hash;
    }

    private static int hashCode(List<Argument<?>> arguments, @Nullable Scope scope) {
        int hash = 1;
        for (Argument<?> argument : arguments) {
            hash = 31 * hash + hashCode(argument, scope);
        }
        return hash;
    }

    static @Nullable Argument<?> componentType(Argument<?> array) {
        if (array instanceof WildcardArgument<?>) {
            return null;
        }
        Class<?> component = array.getType().getComponentType();
        if (component == null) {
            return null;
        }
        return rebuild(array, component);
    }

    static Argument<?> arrayType(Argument<?> component) {
        if (component instanceof WildcardArgument<?> || component.getType() == void.class) {
            throw new IllegalStateException("There is no array of a wildcard or of void: " + component);
        }
        return rebuild(component, component.getType().arrayType());
    }

    /**
     * The argument over another class, an array of its type or the component of it, written the way the argument
     * was: an array has the type arguments of its component, and an array of a variable is a placeholder of that
     * variable.
     */
    private static Argument<?> rebuild(Argument<?> argument, Class<?> type) {
        Argument<?>[] typeParameters = argument.getTypeParameters();
        if (isVariableOrArrayOfVariable(argument)) {
            GenericPlaceholder<?> placeholder = (GenericPlaceholder<?>) argument;
            return Argument.ofTypeVariable(type, null, placeholder.getVariableName(), null,
                typeParameters, placeholder.getBounds().toArray(Argument.ZERO_ARGUMENTS));
        }
        if (argument.isRawType()) {
            return Argument.ofRawType(type, null, null, typeParameters);
        }
        if (typeParameters.length == 0) {
            return Argument.of(type);
        }
        return Argument.of(type, (String) null, typeParameters);
    }

    /**
     * The type variables whose bounds are being read, innermost first.
     *
     * @param name  The name of the variable
     * @param outer The variables around it
     */
    private record Scope(String name, @Nullable Scope outer) {

        static boolean declares(@Nullable Scope scope, String name) {
            for (Scope each = scope; each != null; each = each.outer) {
                if (each.name.equals(name)) {
                    return true;
                }
            }
            return false;
        }
    }
}
