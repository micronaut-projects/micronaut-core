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
package io.micronaut.context;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ConstructorInjectionPoint;


/**
 * Represents an injection point for a bean produced from a field.
 *
 * @param <T> The field type
 * @author Graeme Rocher
 * @since 3.0
 */
@Internal
final class DefaultFieldConstructorInjectionPoint<T> extends DefaultFieldInjectionPoint<T, T> implements ConstructorInjectionPoint<T> {
    /**
     * @param declaringBean      The declaring bean
     * @param declaringType      The declaring type
     * @param fieldType          The field type
     * @param field              The name of the field
     * @param annotationMetadata The annotation metadata
     */
    DefaultFieldConstructorInjectionPoint(
            BeanDefinition<?> declaringBean,
            Class<?> declaringType,
            Class<T> fieldType,
            String field,
            @Nullable AnnotationMetadata annotationMetadata) {
        super(declaringBean, declaringType, fieldType, field, annotationMetadata, Argument.ZERO_ARGUMENTS);
    }

    @Override
    public Argument<?>[] getArguments() {
        return Argument.ZERO_ARGUMENTS;
    }

    @Override
    public T invoke(Object... args) {
        throw new UnsupportedOperationException("Use BeanFactory.instantiate(..) instead");
    }
}
