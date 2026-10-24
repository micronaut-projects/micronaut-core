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
package io.micronaut.aop;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Executable;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.function.Function;

/**
 * Extended version of {@link InvocationContext} for {@link MethodInterceptor} instances.
 *
 * @param <T> The declaring type
 * @param <R> The result of the method call
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MethodInvocationContext<T, R> extends InvocationContext<T, R>, Executable<T, R>, ExecutableMethod<T, R> {

    /**
     * The underlying {@link ExecutableMethod} reference.
     *
     * @return The underlying method reference.
     */
    ExecutableMethod<T, R> getExecutableMethod();

    @Override
    default boolean isSuspend() {
        return getExecutableMethod().isSuspend();
    }

    @Override
    default boolean isAbstract() {
        return getExecutableMethod().isAbstract();
    }

    @Override
    default Class<T> getDeclaringType() {
        return getExecutableMethod().getDeclaringType();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The value is kept by the {@link #getExecutableMethod() executable method} when the invocation sees its
     * metadata, and is computed for the invocation otherwise, so that evaluated expressions see its target and
     * arguments.</p>
     */
    @Experimental
    @Override
    default <V extends @Nullable Object> V getCachedValue(Class<V> key, Function<? super ExecutableMethod<T, R>, ? extends V> factory) {
        ExecutableMethod<T, R> executableMethod = getExecutableMethod();
        if (executableMethod == this || executableMethod.getAnnotationMetadata() != getAnnotationMetadata()) {
            return factory.apply(this);
        }
        return executableMethod.getCachedValue(key, factory);
    }

    @Experimental
    @Override
    default <A extends Annotation> @Nullable AnnotationValue<A> getCachedAnnotationValue(Class<A> annotationType) {
        ExecutableMethod<T, R> executableMethod = getExecutableMethod();
        if (executableMethod == this || executableMethod.getAnnotationMetadata() != getAnnotationMetadata()) {
            return getAnnotation(annotationType);
        }
        return executableMethod.getCachedAnnotationValue(annotationType);
    }

    @Experimental
    @Override
    default <A extends Annotation> @Nullable A getCachedAnnotation(Class<A> annotationType) {
        ExecutableMethod<T, R> executableMethod = getExecutableMethod();
        if (executableMethod == this || executableMethod.getAnnotationMetadata() != getAnnotationMetadata()) {
            return ExecutableMethod.super.getCachedAnnotation(annotationType);
        }
        return executableMethod.getCachedAnnotation(annotationType);
    }
}
