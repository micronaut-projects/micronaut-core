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

import java.io.Serializable;
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
 * order; a type reached twice (a diamond) is kept where it was first reached. A primitive has no super type. An
 * array is assignable to the arrays of its component's closure, then to the arrays of fewer dimensions of
 * {@code Object}, {@code Cloneable} and {@code Serializable}, and to those three types themselves:
 * {@code List<String>[]} to {@code Collection<String>[]}, {@code Iterable<String>[]}, {@code Object[]},
 * {@code Cloneable}, {@code Serializable} and {@code Object}.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class TypeClosure {

    private static final String OBJECT = Object.class.getName();
    private static final Class<?>[] ARRAY_TYPES = {Object.class, Cloneable.class, Serializable.class};

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
                extended.put(key(element), element);
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
                ClassElement array = arrayOf(element, dimensions);
                closure.putIfAbsent(key(array), array);
            }
            // an array of fewer dimensions is an Object, a Cloneable and a Serializable, so an array of more is
            // assignable to their arrays of that many dimensions, and every array to the three types themselves
            for (int fewer = dimensions - 1; fewer >= 0; fewer--) {
                for (Class<?> arrayType : ARRAY_TYPES) {
                    if (fewer == 0 && arrayType == Object.class) {
                        continue;
                    }
                    ClassElement array = arrayOf(ClassElement.of(arrayType), fewer);
                    closure.putIfAbsent(key(array), array);
                }
            }
            return closure;
        }
        if (isObject(type) || closure.putIfAbsent(key(type), type) != null || type.isPrimitive()) {
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

    private static ClassElement arrayOf(ClassElement element, int dimensions) {
        ClassElement array = element;
        for (int i = 0; i < dimensions; i++) {
            array = array.toArray();
        }
        return array;
    }

    /**
     * What tells two entries apart: the name, and the dimensions since the name of an array is its component's.
     */
    private static String key(ClassElement type) {
        return type.getName() + "[]".repeat(type.getArrayDimensions());
    }
}
