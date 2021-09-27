/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Objects;

/**
 * Abstract implementation of {@link io.micronaut.inject.ast.ClassElement} that uses reflection.
 *
 * @param <T> The generic type
 * @since 3.1.0
 * @author Jonas Konrad
 */
@Internal
@Experimental
abstract class ReflectTypeElement<T extends Type> implements ClassElement {
    protected final T type;

    ReflectTypeElement(T type) {
        this.type = type;
    }

    final Class<?> getErasure() {
        Class<?> erasure = getErasure(type);
        for (int i = 0; i < getArrayDimensions(); i++) {
            erasure = Array.newInstance(erasure, 0).getClass();
        }
        return erasure;
    }

    @Override
    public boolean isPrimitive() {
        return getErasure().isPrimitive();
    }

    @Override
    public boolean isPackagePrivate() {
        int modifiers = getErasure().getModifiers();
        return !Modifier.isPublic(modifiers) && !Modifier.isProtected(modifiers) && !Modifier.isPrivate(modifiers);
    }

    @Override
    public boolean isProtected() {
        return !isPublic();
    }

    @Override
    public boolean isPublic() {
        return Modifier.isPublic(getErasure().getModifiers());
    }

    @Override
    public String toString() {
        return type.getTypeName();
    }

    @NonNull
    @Override
    public String getName() {
        Class<?> erasure = getErasure();
        // unwrap arrays, consistent with JavaClassElement
        while (erasure.isArray()) {
            erasure = erasure.getComponentType();
        }
        return erasure.getName();
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
    public boolean isAssignable(Class<?> type) {
        return type.isAssignableFrom(getErasure());
    }

    @Override
    public boolean isAssignable(String type) {
        // unsupported by this impl
        return false;
    }

    @Override
    public boolean isAssignable(ClassElement type) {
        // unsupported by this impl
        return false;
    }

    @NonNull
    @Override
    public Object getNativeType() {
        return type;
    }

    @NonNull
    @Override
    public ClassElement getRawClassElement() {
        return ClassElement.of(getErasure());
    }

    /**
     * Gets the erasure for the given type.
     * @param type The type
     * @return The erased class, never {@code null}
     */
    static @NonNull Class<?> getErasure(@NonNull Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        } else if (type instanceof GenericArrayType) {
            return Array.newInstance(getErasure(((GenericArrayType) type).getGenericComponentType()), 0).getClass();
        } else if (type instanceof ParameterizedType) {
            return getErasure(((ParameterizedType) type).getRawType());
        } else if (type instanceof TypeVariable<?>) {
            return getErasure(((TypeVariable<?>) type).getBounds()[0]);
        } else if (type instanceof WildcardType) {
            return getErasure(((WildcardType) type).getUpperBounds()[0]);
        } else {
            throw new IllegalArgumentException("Unsupported type: " + type.getClass());
        }
    }
}
