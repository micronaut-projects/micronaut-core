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
package io.micronaut.reflection;

import io.micronaut.context.EnvironmentConfigurable;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.AbstractEnvironmentAnnotationMetadata;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * An {@link io.micronaut.inject.ExecutableMethod} over a {@link Method}, with the arguments, the return type
 * and the annotation metadata a generated executable method would have.
 *
 * @param <T> The declaring type
 * @param <R> The return type
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public final class ReflectionExecutableMethod<T, R> extends AbstractReflectionExecutable<T, R> implements EnvironmentConfigurable {

    private static final String KOTLIN_CONTINUATION = "kotlin.coroutines.Continuation";

    private final Method method;
    private final AnnotationMetadata annotationMetadata;
    private final boolean suspend;
    @Nullable
    private volatile Environment environment;

    /**
     * Creates an executable method over a method.
     *
     * @param declaringType The type the method is invoked on, which may inherit the method
     * @param method        The method
     */
    @SuppressWarnings("unchecked")
    public ReflectionExecutableMethod(Class<T> declaringType, Method method) {
        super(declaringType,
            method.getName(),
            (Argument<R>) ReflectionArguments.returnOf(method),
            ReflectionArguments.argumentsOf(method));
        this.method = method;
        method.trySetAccessible();
        AnnotationMetadata metadata = ReflectionAnnotations.metadataOf(method);
        // as for a generated method, the property expressions of the metadata are resolved against the
        // environment the method is configured with
        this.annotationMetadata = metadata.hasPropertyExpressions() ? new MethodAnnotationMetadata(metadata) : metadata;
        Class<?>[] parameterTypes = method.getParameterTypes();
        this.suspend = parameterTypes.length > 0 && KOTLIN_CONTINUATION.equals(parameterTypes[parameterTypes.length - 1].getName());
    }

    /**
     * The executable method of a method, declared by the class declaring the method.
     *
     * @param method The method
     * @param <T>    The declaring type
     * @return The executable method
     */
    @SuppressWarnings("unchecked")
    public static <T> ReflectionExecutableMethod<T, Object> of(Method method) {
        return new ReflectionExecutableMethod<>((Class<T>) method.getDeclaringClass(), method);
    }

    /**
     * The executable method of a method, declared by the given type.
     *
     * @param declaringType The type the method is invoked on, which may inherit the method
     * @param method        The method
     * @param <T>           The declaring type
     * @return The executable method
     */
    public static <T> ReflectionExecutableMethod<T, Object> of(Class<T> declaringType, Method method) {
        return new ReflectionExecutableMethod<>(declaringType, method);
    }

    /**
     * The method this executable method invokes.
     *
     * @return The method
     */
    public Method getMethod() {
        return method;
    }

    @Override
    public Method getTargetMethod() {
        return method;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public boolean isAbstract() {
        return Modifier.isAbstract(method.getModifiers());
    }

    @Override
    public boolean isSuspend() {
        return suspend;
    }

    @Override
    public boolean hasPropertyExpressions() {
        return annotationMetadata.hasPropertyExpressions();
    }

    @Override
    public void configure(Environment environment) {
        this.environment = environment;
    }

    @Override
    @Nullable
    @SuppressWarnings("NullAway") // a method can return null
    public R invokeUnsafe(T instance, @Nullable Object... arguments) {
        return ReflectionUtils.invokeMethod(instance, method, arguments);
    }

    /**
     * The metadata of the method resolving its property expressions against the configured environment.
     */
    private final class MethodAnnotationMetadata extends AbstractEnvironmentAnnotationMetadata {

        MethodAnnotationMetadata(AnnotationMetadata targetMetadata) {
            super(targetMetadata);
        }

        @Override
        @Nullable
        protected Environment getEnvironment() {
            return environment;
        }
    }
}
