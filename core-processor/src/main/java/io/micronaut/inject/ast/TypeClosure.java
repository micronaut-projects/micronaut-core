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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The type closure of a type: the type, every super class and interface above it, each once, and
 * {@code Object} last, with the type arguments the type gives each of them along the path.
 *
 * <p>This is the structure of the hierarchy, walked down {@link ClassElement#getSuperType()} and
 * {@link ClassElement#getInterfaces()}: which types, in which order, each as the element the language resolved
 * for it. The arguments the type gives each of them are what {@link ClassElement#getAllTypeArguments()} answers
 * under the type's name, which binds the variables of every super type the whole way up for every language; a
 * caller writing the closure takes the elements from here and the arguments from there. A type written raw
 * reaches its super types with its variables raw, which is the erasure the language gives them (JLS 4.8), and a
 * wildcard stays a wildcard.</p>
 *
 * <p>The order is the super class before the interfaces, each followed by its own super types, in declaration
 * order; a type reached twice (a diamond) is kept where it was first reached. A primitive has no super type, and
 * an array is assignable to the arrays of its component's closure: {@code List<String>[]} to
 * {@code Collection<String>[]}, {@code Iterable<String>[]}, {@code Object[]} and {@code Object}.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class TypeClosure {

    private static final String OBJECT = Object.class.getName();

    private TypeClosure() {
    }

    /**
     * The type closure of the type, the type first and {@code Object} last.
     *
     * @param type The type
     * @return The closure, never empty
     */
    @NonNull
    public static List<ClassElement> of(@NonNull ClassElement type) {
        return withObject(collect(type, new LinkedHashMap<>()));
    }

    /**
     * Adds the closure of another type the first one is assignable to, keeping what the first closure already
     * holds: the proxy of an interface that implements further interfaces is assignable to them too.
     *
     * @param closure The closure to extend, {@code Object} last
     * @param type    The type to add the closure of
     * @return The extended closure, {@code Object} last
     */
    @NonNull
    public static List<ClassElement> add(@NonNull List<ClassElement> closure, @NonNull ClassElement type) {
        Map<String, ClassElement> extended = new LinkedHashMap<>();
        for (ClassElement element : closure) {
            if (!isObject(element)) {
                extended.put(element.getName(), element);
            }
        }
        return withObject(collect(type, extended));
    }

    private static List<ClassElement> withObject(Map<String, ClassElement> closure) {
        List<ClassElement> result = new ArrayList<>(closure.size() + 1);
        result.addAll(closure.values());
        result.add(ClassElement.of(Object.class));
        return result;
    }

    private static Map<String, ClassElement> collect(ClassElement type, Map<String, ClassElement> closure) {
        if (type.isArray()) {
            int dimensions = type.getArrayDimensions();
            ClassElement component = type;
            while (component.isArray()) {
                component = component.fromArray();
            }
            Map<String, ClassElement> componentClosure = collect(component, new LinkedHashMap<>());
            if (!component.isPrimitive()) {
                componentClosure.put(OBJECT, ClassElement.of(Object.class));
            }
            for (ClassElement element : componentClosure.values()) {
                ClassElement array = element;
                for (int i = 0; i < dimensions; i++) {
                    array = array.toArray();
                }
                closure.putIfAbsent(element.getName(), array);
            }
            return closure;
        }
        if (isObject(type) || closure.putIfAbsent(type.getName(), type) != null || type.isPrimitive()) {
            return closure;
        }
        type.getSuperType().ifPresent(superType -> collect(superType, closure));
        for (ClassElement anInterface : type.getInterfaces()) {
            collect(anInterface, closure);
        }
        return closure;
    }

    private static boolean isObject(ClassElement type) {
        return !type.isArray() && OBJECT.equals(type.getName());
    }
}
