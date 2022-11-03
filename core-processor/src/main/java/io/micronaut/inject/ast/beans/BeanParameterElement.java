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
package io.micronaut.inject.ast.beans;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ParameterElement;

/**
 * Represents a configurable bean parameter.
 *
 * @author graemerocher
 * @since 3.0.0
 */
public interface BeanParameterElement extends ParameterElement, InjectableElement {
    @Override
    default InjectableElement injectValue(String expression) {
        return InjectableElement.super.injectValue(expression);
    }

    @NonNull
    @Override
    default BeanParameterElement qualifier(@Nullable String qualifier) {
        return (BeanParameterElement) InjectableElement.super.qualifier(qualifier);
    }

    @NonNull
    @Override
    default BeanParameterElement qualifier(@NonNull AnnotationValue<?> qualifier) {
        return (BeanParameterElement) InjectableElement.super.qualifier(qualifier);
    }
}
