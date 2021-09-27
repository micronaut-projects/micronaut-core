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
import io.micronaut.core.annotation.NonNull;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ClassElement} backed by reflection.
 *
 * @author graemerocher
 * @since 2.3
 */
@Internal
class ReflectClassElement extends ReflectTypeElement<Class<?>> {
    /**
     * Default constructor.
     *
     * @param type The type
     */
    ReflectClassElement(Class<?> type) {
        super(type);
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
    public ClassElement toArray() {
        Class<?> arrayType = Array.newInstance(type, 0).getClass();
        return ClassElement.of(arrayType);
    }

    @Override
    public ClassElement fromArray() {
        return new ReflectClassElement(type.getComponentType());
    }

    @NonNull
    @Override
    public List<? extends FreeTypeVariableElement> getDeclaredTypeVariables() {
        return Arrays.stream(type.getTypeParameters())
                .map(tv -> new ReflectFreeTypeVariableElement(tv, 0))
                .collect(Collectors.toList());
    }
}
