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
import org.jspecify.annotations.Nullable;
import io.micronaut.core.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Binds the type variables a super type leaves in the arguments it reports for the types above it, for
 * {@link ClassElement#getAllTypeArguments()} and the language implementations that walk their super types
 * themselves.
 *
 * @author Micronaut
 * @since 5.2.2
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
            if (placeholder.isRawType()) {
                // the variable of a type written raw stands for its erasure, whatever a variable of the same name is bound to
                return type;
            }
            ClassElement binding = bindings.get(placeholder.getVariableName());
            if (binding == null || isSameVariable(binding, placeholder)) {
                // unbound, or an unresolved use bound to the variable itself: the use is kept, so that nothing above it
                // is copied for no change
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
        if (type instanceof WildcardElement wildcard) {
            // The wildcard is kept as it is when none of its bounds changes: the language element knows its variance,
            // and a copy of the type holding it would resolve the variables inside it by name again, against the
            // arguments of that type, where a variable of the first type can share its name with a parameter of the
            // copied one
            if (bind(wildcard.getUpperBounds(), bindings) == null && bind(wildcard.getLowerBounds(), bindings) == null) {
                return type;
            }
            ClassElement folded = type.foldBoundGenericTypes(bound -> bound instanceof GenericPlaceholderElement ? bind(bound, bindings) : bound);
            return folded == null ? type : folded;
        }
        Map<String, ClassElement> typeArguments = type.getTypeArguments();
        Map<String, ClassElement> boundTypeArguments = bind(typeArguments, bindings);
        return boundTypeArguments == typeArguments ? type : copy(type, bound -> bound.withTypeArguments(boundTypeArguments));
    }

    /**
     * Whether a binding is the variable it binds, a use of a variable bound to itself.
     */
    private static boolean isSameVariable(ClassElement binding, GenericPlaceholderElement use) {
        // a use that is resolved, as the Kotlin element resolves a use to its bound, is replaced by the declaration
        return use.getResolved().isEmpty()
            && binding instanceof GenericPlaceholderElement bound
            && bound.getResolved().isEmpty()
            && bound.getVariableName().equals(use.getVariableName())
            && bound.getArrayDimensions() == 0
            && bound.getGenericTypeAnnotationMetadata().getAnnotationMetadata().getAnnotationNames().isEmpty();
    }

    /**
     * Binds each of the bounds of a wildcard.
     *
     * @param bounds   The bounds
     * @param bindings The bindings
     * @return The bound bounds, or {@code null} when none changed
     */
    @Nullable
    private static List<ClassElement> bind(List<? extends ClassElement> bounds, Map<String, ClassElement> bindings) {
        List<ClassElement> bound = null;
        for (int i = 0; i < bounds.size(); i++) {
            ClassElement each = bounds.get(i);
            ClassElement boundEach = bind(each, bindings);
            if (boundEach != each && bound == null) {
                bound = new ArrayList<>(bounds.subList(0, i));
            }
            if (bound != null) {
                bound.add(boundEach);
            }
        }
        return bound;
    }

    /**
     * The type a variable is bound to, unless the use of the variable annotates it: the annotations of a use are
     * carried by the element of the use alone, and the use already stands for what the variable is bound to, so
     * answering it keeps them. Such a use is answered with the variable name of the type that writes it, which is
     * the name the generated argument carries for an annotated type argument anyway.
     *
     * @param bound The type the variable is bound to
     * @param use   The use of the variable
     * @return The bound type, or the use when it annotates the variable
     */
    private static ClassElement readThroughUse(ClassElement bound, GenericPlaceholderElement use) {
        if (use.getGenericTypeAnnotationMetadata().getAnnotationMetadata().getAnnotationNames().isEmpty()) {
            return bound;
        }
        // Matching annotation names on the binding do not make the occurrence redundant: its member
        // values may differ, and must still take precedence over the binding's values.
        return use;
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
    private static ClassElement copy(ClassElement type, UnaryOperator<ClassElement> copy) {
        try {
            return copy.apply(type);
        } catch (UnsupportedOperationException e) {
            return type;
        }
    }
}
