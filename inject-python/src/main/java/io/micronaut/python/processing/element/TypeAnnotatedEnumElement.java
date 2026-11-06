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
package io.micronaut.python.processing.element;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.EnumElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import java.util.List;
import java.util.Optional;

/**
 * Enum element wrapper that applies additional type-use annotation metadata.
 */
@Internal
@Experimental
@SuppressWarnings("java:S2160") // EnumElement equality is defined by the delegated element model.
public final class TypeAnnotatedEnumElement extends TypeAnnotatedClassElement implements EnumElement {

    private final EnumElement delegate;

    TypeAnnotatedEnumElement(EnumElement delegate, ElementAnnotationMetadata typeAnnotationMetadata) {
        super(delegate, typeAnnotationMetadata);
        this.delegate = delegate;
    }

    @Override
    public List<String> values() {
        return delegate.values();
    }

    @Override
    public List<EnumConstantElement> elements() {
        return delegate.elements();
    }

    @Override
    public Optional<MethodElement> getEnumValueOfMethod() {
        return delegate.getEnumValueOfMethod();
    }
}
