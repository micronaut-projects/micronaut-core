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

import io.micronaut.core.annotation.NonNull;

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class ReflectFreeTypeVariableElement extends ReflectTypeElement<TypeVariable<?>> implements FreeTypeVariableElement, ArrayableClassElement {
    private final int arrayDimensions;

    ReflectFreeTypeVariableElement(TypeVariable<?> typeVariable, int arrayDimensions) {
        super(typeVariable);
        this.arrayDimensions = arrayDimensions;
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return new ReflectFreeTypeVariableElement(type, arrayDimensions);
    }

    @Override
    public int getArrayDimensions() {
        return arrayDimensions;
    }

    @NonNull
    @Override
    public List<? extends ClassElement> getBounds() {
        return Arrays.stream(type.getBounds()).map(ClassElement::of).collect(Collectors.toList());
    }

    @Override
    public String getVariableName() {
        return type.getName();
    }

    @Override
    public Optional<Element> getDeclaringElement() {
        GenericDeclaration declaration = type.getGenericDeclaration();
        if (declaration instanceof Class) {
            return Optional.of(ClassElement.of((Class<?>) declaration));
        } else {
            return Optional.empty();
        }
    }
}
