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
package io.micronaut.core.beans;

import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GenericPlaceholder;
import io.micronaut.core.type.TypeHierarchies;
import io.micronaut.core.type.TypeHierarchy;

import java.util.List;
import java.util.Optional;

/**
 * The erasure match of {@link BeanTypeHierarchy#findOverridden(BeanIntrospector, Class, String, Class[])}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
final class BeanTypeHierarchies {

    private BeanTypeHierarchies() {
    }

    /**
     * Whether a method of the introspected type with the given parameter types overrides a declaration of a level
     * with the given arguments: each argument is the very parameter type, or a type variable of the level that
     * the introspected type binds to it, or leaves open with an erasure accepting it.
     *
     * @param hierarchy      The hierarchy of the introspected type
     * @param level          The level declaring the candidate
     * @param arguments      The arguments of the candidate
     * @param parameterTypes The parameter types of the overriding method
     * @return Whether the candidate is overridden
     */
    static boolean erasesTo(BeanTypeHierarchy hierarchy, Class<?> level, Argument<?>[] arguments, Class<?>[] parameterTypes) {
        for (int i = 0; i < arguments.length; i++) {
            Argument<?> argument = arguments[i];
            Class<?> parameterType = parameterTypes[i];
            if (argument.getType() == parameterType) {
                continue;
            }
            if (!argument.isUnresolvedTypeVariable() || !(argument instanceof GenericPlaceholder<?> placeholder)) {
                return false;
            }
            Optional<Class<?>> bound = boundType(hierarchy, level, placeholder.getVariableName());
            if (bound.isPresent() ? bound.get() != parameterType : !argument.getType().isAssignableFrom(parameterType)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The type the introspected type gives a type variable of a level, empty when it is left open or the generic
     * hierarchy is not recorded.
     */
    private static Optional<Class<?>> boundType(BeanTypeHierarchy hierarchy, Class<?> level, String variable) {
        Optional<TypeHierarchy> levelHierarchy = TypeHierarchies.find(level);
        if (levelHierarchy.isEmpty()) {
            return Optional.empty();
        }
        List<Argument<?>> variables = levelHierarchy.get().getTypeVariables();
        int index = -1;
        for (int i = 0; i < variables.size(); i++) {
            Argument<?> candidate = variables.get(i);
            String name = candidate instanceof GenericPlaceholder<?> p ? p.getVariableName() : candidate.getName();
            if (name.equals(variable)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return Optional.empty();
        }
        for (Argument<?> superType : hierarchy.getTypeClosure()) {
            if (superType.getType() == level) {
                Argument<?>[] typeArguments = superType.getTypeParameters();
                if (index >= typeArguments.length || typeArguments[index].isUnresolvedTypeVariable() || typeArguments[index].isWildcard()) {
                    return Optional.empty();
                }
                return Optional.of(typeArguments[index].getType());
            }
        }
        return Optional.empty();
    }
}
