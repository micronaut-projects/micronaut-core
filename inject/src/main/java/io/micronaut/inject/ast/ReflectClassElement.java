/*
 * Copyright 2017-2020 original authors
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
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.Objects;

/**
 * Implementation of {@link ClassElement} backed by reflection.
 *
 * @author graemerocher
 * @since 2.3
 */
@Internal
final class ReflectClassElement implements ClassElement {
    private final Class<?> type;

    /**
     * Default constructor.
     * @param type The type
     */
    ReflectClassElement(Class<?> type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return type.getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ReflectClassElement that = (ReflectClassElement) o;
        return type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }

    @Override
    public boolean isPrimitive() {
        return type.isPrimitive();
    }

    @Override
    public boolean isArray() {
        return type.isArray();
    }

    @Override
    public int getArrayDimensions() {
        return computeDimensions(type);
    }

    private int computeDimensions(Class<?> type) {
        int i = 0;
        while (type.isArray()) {
            i++;
            type = type.getComponentType();
        }
        return i;
    }

    @Override
    public boolean isAssignable(String type) {
        throw new UnsupportedOperationException("isAssignable via String is not supported by this implementation");
    }

    @Override
    public ClassElement toArray() {
        Class<?> arrayType = Array.newInstance(type, 0).getClass();
        return ClassElement.of(arrayType);
    }

    @Override
    public ClassElement fromArray() {
        return new ReflectClassElement(type.getComponentType());
    }

    @NotNull
    @Override
    public String getName() {
        return type.getName();
    }

    @Override
    public boolean isProtected() {
        return !isPublic();
    }

    @Override
    public boolean isPublic() {
        return Modifier.isPublic(type.getModifiers());
    }

    @NotNull
    @Override
    public Object getNativeType() {
        return type;
    }
}
