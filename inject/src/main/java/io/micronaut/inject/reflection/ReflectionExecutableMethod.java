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
package io.micronaut.inject.reflection;

import io.micronaut.context.AbstractExecutableMethod;
import io.micronaut.context.AnnotationReflectionUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.ReflectionAnnotationMetadataBuilder;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * An {@link io.micronaut.inject.ExecutableMethod} over a {@link Method}, with the arguments, the return type
 * and the annotation metadata a generated executable method would have.
 *
 * @param <T> The declaring type
 * @param <R> The return type
 * @author Denis Stepanov
 * @since 5.1
 */
@Experimental
public final class ReflectionExecutableMethod<T, R> extends AbstractExecutableMethod<T, R> {

    private final Method method;
    private volatile @Nullable AnnotationMetadata annotationMetadata;

    /**
     * @param declaringType The type the method is invoked on, which may inherit the method
     * @param method        The method
     */
    @SuppressWarnings("unchecked")
    public ReflectionExecutableMethod(Class<T> declaringType, Method method) {
        super(declaringType,
            method.getName(),
            (Argument<R>) AnnotationReflectionUtils.returnArgumentOf(method),
            AnnotationReflectionUtils.argumentsOf(method));
        this.method = method;
        method.trySetAccessible();
    }

    /**
     * @return The method
     */
    public Method getMethod() {
        return method;
    }

    @Override
    @SuppressWarnings("NullAway") // a method can return null, the declaration of invokeInternal predates the nullness annotations
    protected R invokeInternal(T instance, @Nullable Object[] arguments) {
        return ReflectionUtils.invokeMethod(instance, method, arguments);
    }

    @Override
    protected Method resolveTargetMethod() {
        return method;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        // the base class resolves the metadata while constructing, before the method is known: read it lazily instead
        AnnotationMetadata metadata = annotationMetadata;
        if (metadata == null) {
            metadata = ReflectionAnnotationMetadataBuilder.build(method);
            annotationMetadata = metadata;
        }
        return metadata;
    }
}
