/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing.element;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The generic bindings a declaring type contributes to its members.
 */
final class GenericBindings {

    private GenericBindings() {
    }

    /**
     * The declared type variables of a type, bound to themselves or to their first bound.
     *
     * @param declaringType The declaring type
     * @param preservePlaceholders Whether a variable stays a placeholder instead of its first bound
     * @return The bindings by variable name
     */
    static Map<String, ClassElement> declared(ClassElement declaringType, boolean preservePlaceholders) {
        List<? extends GenericPlaceholderElement> placeholders = declaringType.getDeclaredGenericPlaceholders();
        if (placeholders.isEmpty()) {
            return Map.of();
        }
        Map<String, ClassElement> bindings = new LinkedHashMap<>(placeholders.size());
        for (GenericPlaceholderElement placeholder : placeholders) {
            bindings.put(
                placeholder.getVariableName(),
                preservePlaceholders ? placeholder : firstBound(placeholder)
            );
        }
        return bindings;
    }

    /**
     * @param placeholder A type variable
     * @return Its first bound, or {@code Object} when it has none
     */
    static ClassElement firstBound(GenericPlaceholderElement placeholder) {
        List<? extends ClassElement> bounds = placeholder.getBounds();
        if (bounds.isEmpty()) {
            return ClassElement.of(Object.class);
        }
        return bounds.getFirst();
    }
}
