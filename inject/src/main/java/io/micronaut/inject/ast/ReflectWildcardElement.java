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

import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

class ReflectWildcardElement extends ReflectTypeElement<WildcardType> implements WildcardElement {
    ReflectWildcardElement(WildcardType type) {
        super(type);
    }

    @NonNull
    @Override
    public ClassElement toArray() {
        throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public ClassElement fromArray() {
        throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public List<? extends ClassElement> getUpperBounds() {
        return Arrays.stream(type.getUpperBounds()).map(ClassElement::of).collect(Collectors.toList());
    }

    @NonNull
    @Override
    public List<? extends ClassElement> getLowerBounds() {
        return Arrays.stream(type.getLowerBounds()).map(ClassElement::of).collect(Collectors.toList());
    }
}
