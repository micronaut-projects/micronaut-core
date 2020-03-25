/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ConstructorInjectionPoint;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A {@link ConstructorInjectionPoint} that invokes a method without relying on reflection.
 *
 * @author graemerocher
 * @since 1.0
 * @param <T> The constructed type
 */
@Internal
class DefaultMethodConstructorInjectionPoint<T> extends DefaultMethodInjectionPoint<T, T> implements ConstructorInjectionPoint<T> {

    /**
     * @param declaringBean      The declaring bean
     * @param declaringType      The declaring bean type
     * @param methodName         The method name
     * @param arguments          The arguments
     * @param annotationMetadata The annotation metadata
     */
    DefaultMethodConstructorInjectionPoint(
        BeanDefinition declaringBean,
        Class<?> declaringType,
        String methodName,
        @Nullable Argument[] arguments,
        @Nullable AnnotationMetadata annotationMetadata) {
        super(declaringBean, declaringType, methodName, arguments, annotationMetadata);
    }

    @Override
    public T invoke(Object... args) {
        throw new UnsupportedOperationException("Use MethodInjectionPoint#invoke(..) instead");
    }
}
