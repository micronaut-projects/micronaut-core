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
package io.micronaut.core.beans;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;

import java.util.Objects;

/**
 * Abstract implementation of the {@link BeanConstructor} interface.
 *
 * @param <T> The bean type
 * @author graemerocher
 * @since 3.0.0
 */
@UsedByGeneratedCode
public abstract class AbstractBeanConstructor<T> implements BeanConstructor<T> {
    private final Class<T> beanType;
    private final AnnotationMetadata annotationMetadata;
    private final Argument<?>[] arguments;

    /**
     * Default constructor.
     * @param beanType The bean type
     * @param annotationMetadata The annotation metadata
     * @param arguments The arguments
     */
    protected AbstractBeanConstructor(
            Class<T> beanType,
            AnnotationMetadata annotationMetadata,
            Argument<?>... arguments) {
        this.beanType = Objects.requireNonNull(beanType, "Bean type should not be null");
        this.annotationMetadata = annotationMetadata == null ? AnnotationMetadata.EMPTY_METADATA : annotationMetadata;
        this.arguments = ArrayUtils.isEmpty(arguments) ? Argument.ZERO_ARGUMENTS : arguments;
    }

    @Override
    @NonNull
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    @NonNull
    public Class<T> getDeclaringBeanType() {
        return beanType;
    }

    @Override
    @NonNull
    public Argument<?>[] getArguments() {
        return arguments;
    }
}
