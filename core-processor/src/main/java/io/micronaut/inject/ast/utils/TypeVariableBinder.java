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
package io.micronaut.inject.ast.utils;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.WildcardElement;

import java.util.Map;

/**
 * Binds the type variables a super type leaves in the arguments it reports for the types above it, for
 * {@link ClassElement#getAllTypeArguments()} and the language implementations that walk their super types
 * themselves.
 *
 * @author Micronaut
 * @since 5.2.0
 */
@Internal
public final class TypeVariableBinder {

    private TypeVariableBinder() {
    }

    /**
     * Replaces the type variables named in the bindings, all at once, so a variable a binding introduces is never
     * replaced again: {@code [K, V]} bound with {@code {K=V, V=K}} gives {@code [V, K]}.
     *
     * <p>The arguments passed in must be the ones the type declares, written in its own variables, not ones a
     * caller resolved already: a resolved argument bound a second time is replaced again, and swaps back
     * whenever the variable names of the two types collide.</p>
     *
     * @param typeArguments The type arguments to bind
     * @param bindings      The types bound to the variables, by variable name
     * @return The bound type arguments, the same map if no variable was bound
     */
    @NonNull
    public static Map<String, ClassElement> bind(@NonNull Map<String, ClassElement> typeArguments,
                                                 @NonNull Map<String, ClassElement> bindings) {
        if (typeArguments.isEmpty() || bindings.isEmpty()) {
            return typeArguments;
        }
        Map<String, ClassElement> bound = CollectionUtils.newLinkedHashMap(typeArguments.size());
        boolean changed = false;
        for (Map.Entry<String, ClassElement> entry : typeArguments.entrySet()) {
            ClassElement typeArgument = entry.getValue();
            ClassElement boundTypeArgument = bind(typeArgument, bindings);
            changed |= boundTypeArgument != typeArgument;
            bound.put(entry.getKey(), boundTypeArgument);
        }
        return changed ? bound : typeArguments;
    }

    private static ClassElement bind(ClassElement type, Map<String, ClassElement> bindings) {
        if (type instanceof GenericPlaceholderElement placeholder) {
            ClassElement binding = bindings.get(placeholder.getVariableName());
            if (binding == null) {
                return type;
            }
            // A variable resolved through a binding already counts the dimensions of the binding
            ClassElement bound = binding;
            while (bound.getArrayDimensions() < placeholder.getArrayDimensions()) {
                bound = bound.toArray();
            }
            return bound;
        }
        if (type instanceof WildcardElement) {
            ClassElement folded = type.foldBoundGenericTypes(bound -> bound instanceof GenericPlaceholderElement ? bind(bound, bindings) : bound);
            return folded == null ? type : folded;
        }
        Map<String, ClassElement> typeArguments = type.getTypeArguments();
        Map<String, ClassElement> boundTypeArguments = bind(typeArguments, bindings);
        return boundTypeArguments == typeArguments ? type : type.withTypeArguments(boundTypeArguments);
    }
}
