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
import io.micronaut.core.util.CollectionUtils;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

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
                ClassElement array = copy(bound, ClassElement::toArray);
                if (array == bound) {
                    return type;
                }
                bound = array;
            }
            return readThroughUse(bound, placeholder);
        }
        if (type instanceof WildcardElement) {
            ClassElement folded = type.foldBoundGenericTypes(bound -> bound instanceof GenericPlaceholderElement ? bind(bound, bindings) : bound);
            return folded == null ? type : folded;
        }
        Map<String, ClassElement> typeArguments = type.getTypeArguments();
        Map<String, ClassElement> boundTypeArguments = bind(typeArguments, bindings);
        return boundTypeArguments == typeArguments ? type : copy(type, bound -> bound.withTypeArguments(boundTypeArguments));
    }

    /**
     * The type a variable is bound to, keeping the type annotations written where the variable is used: for
     * {@code interface Middle<T> extends Container<@Marker T>} read through {@code class Leaf<X> implements Middle<X>},
     * the {@code Container} argument is {@code X} and is still annotated {@code @Marker}.
     *
     * @param bound The type the variable is bound to
     * @param use   The use of the variable
     * @return The bound type, wrapped only when the use annotates it
     */
    private static ClassElement readThroughUse(ClassElement bound, GenericPlaceholderElement use) {
        Collection<String> useAnnotations = use.getGenericTypeAnnotationMetadata().getAnnotationMetadata().getAnnotationNames();
        if (useAnnotations.isEmpty()
            || bound instanceof WildcardElement
            || bound.getTypeAnnotationMetadata().getAnnotationMetadata().getAnnotationNames().containsAll(useAnnotations)) {
            return bound;
        }
        if (bound instanceof GenericPlaceholderElement boundPlaceholder) {
            return new TypeAnnotatedGenericPlaceholderElement(boundPlaceholder, use);
        }
        return new TypeAnnotatedClassElement(bound, use);
    }

    /**
     * Copies a type, which {@link ClassElement#withTypeArguments(Map)} and {@link ClassElement#toArray()} allow an
     * implementation not to support. A type that cannot be copied is answered unchanged: the variables it holds stay
     * unbound, which is what that type could say about them before.
     *
     * @param type The type to copy
     * @param copy The copy to make
     * @return The copy, or the type itself if it does not support being copied
     */
    private static ClassElement copy(ClassElement type, Function<ClassElement, ClassElement> copy) {
        try {
            return copy.apply(type);
        } catch (UnsupportedOperationException e) {
            return type;
        }
    }
}
