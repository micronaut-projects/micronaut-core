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
package io.micronaut.aop.beandefinition;

import io.micronaut.context.EnvironmentConfigurable;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.type.UnsafeExecutable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.AbstractEnvironmentAnnotationMetadata;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * <p>Abstract base class for generated {@link ExecutableMethod} classes to implement. The generated classes should
 * implement the {@link ExecutableMethod#invoke(Object, Object...)} method at compile time providing direct dispatch
 * of the target method</p>
 *
 * @param <T> The declaring type
 * @param <R> The result of the method call
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
abstract sealed class AbstractExecutableMethod<T, R> implements UnsafeExecutable<T, R>, ExecutableMethod<T, R>, EnvironmentConfigurable, io.micronaut.core.type.Executable<T, R> permits InterceptedDisposeExecutableMethod, InterceptedInitializingExecutableMethod {

    protected final Class<T> declaringType;
    protected final String methodName;
    protected final Class<?>[] argTypes;
    private final ReturnType<R> returnType;
    private final Argument<R> genericReturnType;
    private final int hashCode;
    private final Argument<?>[] arguments;
    @Nullable
    private Environment environment;
    @Nullable
    private AnnotationMetadata methodAnnotationMetadata;
    @Nullable
    private Method method;

    /**
     * Creates a new executable method descriptor.
     *
     * @param declaringType     The declaring type
     * @param methodName        The method name
     * @param genericReturnType The generic return type
     * @param arguments         The arguments
     */
    @SuppressWarnings("WeakerAccess")
    protected AbstractExecutableMethod(Class<T> declaringType,
                                       String methodName,
                                       Argument<R> genericReturnType,
                                       Argument<?>... arguments) {
        Objects.requireNonNull(declaringType, "Declaring type cannot be null");
        Objects.requireNonNull(methodName, "Method name cannot be null");

        this.argTypes = Argument.toClassArray(arguments);
        this.declaringType = declaringType;
        this.methodName = methodName;

        if (ArrayUtils.isNotEmpty(arguments)) {
            this.arguments = arguments;
        } else {
            this.arguments = Argument.ZERO_ARGUMENTS;
        }
        this.genericReturnType = genericReturnType;
        this.returnType = new ReturnTypeImpl();
        int result = ObjectUtils.hash(declaringType, methodName);
        result = 31 * result + Arrays.hashCode(argTypes);
        this.hashCode = result;
    }

    /**
     * Creates a new executable method descriptor assuming a {@code void} return type.
     *
     * @param declaringType The declaring type
     * @param methodName    The method name
     */
    @SuppressWarnings("WeakerAccess")
    protected AbstractExecutableMethod(Class<T> declaringType,
                                       String methodName) {
        this(declaringType, methodName, (Argument<R>) Argument.VOID, Argument.ZERO_ARGUMENTS);
    }

    @Override
    public boolean hasPropertyExpressions() {
        return getAnnotationMetadata().hasPropertyExpressions();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        if (this.methodAnnotationMetadata == null) {
            AnnotationMetadata result;
            AnnotationMetadata annotationMetadata = resolveAnnotationMetadata();
            if (annotationMetadata != AnnotationMetadata.EMPTY_METADATA) {
                if (annotationMetadata.hasPropertyExpressions()) {
                    // we make a copy of the result of annotation metadata which is normally a reference
                    // to the class metadata
                    result = new MethodAnnotationMetadata(annotationMetadata);
                } else {
                    result = annotationMetadata;
                }
            } else {
                result = AnnotationMetadata.EMPTY_METADATA;
            }
            this.methodAnnotationMetadata = result;
        }
        return this.methodAnnotationMetadata;

    }

    @Override
    public void configure(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractExecutableMethod<T, R> that = (AbstractExecutableMethod<T, R>) o;
        return Objects.equals(declaringType, that.declaringType) &&
            Objects.equals(methodName, that.methodName) &&
            Arrays.equals(argTypes, that.argTypes);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        String text = Argument.toString(getArguments());
        return getReturnType().getType().getSimpleName() + " " + getMethodName() + "(" + text + ")";
    }

    @Override
    public ReturnType<R> getReturnType() {
        return returnType;
    }

    @Override
    public Class<?>[] getArgumentTypes() {
        return argTypes;
    }

    @Override
    public Class<T> getDeclaringType() {
        return declaringType;
    }

    @Override
    public String getMethodName() {
        return methodName;
    }

    @Override
    @Nullable
    public final R invoke(T instance, @Nullable Object... arguments) {
        if (arguments.length > 0) {
            ArgumentUtils.validateArguments(this, getArguments(), arguments);
        }
        return invokeInternal(instance, arguments);
    }

    @Override
    @Nullable
    public R invokeUnsafe(T instance, @Nullable Object... arguments) {
        return invokeInternal(instance, arguments);
    }

    /**
     * Invokes the generated method implementation.
     *
     * @param instance  The instance
     * @param arguments The arguments
     * @return The invocation result
     */
    @Nullable
    protected abstract R invokeInternal(T instance, @Nullable Object[] arguments);

    /**
     * Resolves the annotation metadata for this method. Subclasses may override to supply
     * pre-computed metadata.
     *
     * @return The {@link AnnotationMetadata}
     */
    protected AnnotationMetadata resolveAnnotationMetadata() {
        return AnnotationMetadata.EMPTY_METADATA;
    }

    @Override
    public Argument<?>[] getArguments() {
        return arguments;
    }

    /**
     * Soft resolves the target {@link Method} avoiding reflection until as late as possible.
     *
     * @return The method
     * @throws NoSuchMethodError if the method doesn't exist
     */
    @Override
    public final Method getTargetMethod() {
        if (method == null) {
            Method resolvedMethod = ReflectionUtils.getRequiredMethod(declaringType, methodName, argTypes);
            resolvedMethod.setAccessible(true);
            this.method = resolvedMethod;
        }
        return this.method;
    }

    /**
     * A {@link ReturnType} implementation.
     */
    private final class ReturnTypeImpl implements ReturnType<R> {

        @Override
        public Class<R> getType() {
            return genericReturnType.getType();
        }

        @Override
        public boolean isSuspended() {
            return AbstractExecutableMethod.this.isSuspend();
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return AbstractExecutableMethod.this.getAnnotationMetadata();
        }

        @Override
        public Argument<?>[] getTypeParameters() {
            return genericReturnType.getTypeParameters();
        }

        @Override
        public Map<String, Argument<?>> getTypeVariables() {
            return genericReturnType.getTypeVariables();
        }

        @Override
        public Argument<R> asArgument() {
            Map<String, Argument<?>> typeVariables = getTypeVariables();
            Collection<Argument<?>> values = typeVariables.values();
            final AnnotationMetadata annotationMetadata = getAnnotationMetadata();
            return Argument.of(getType(), annotationMetadata, values.toArray(Argument.ZERO_ARGUMENTS));
        }
    }

    /**
     * Internal environment aware annotation metadata delegate.
     */
    private final class MethodAnnotationMetadata extends AbstractEnvironmentAnnotationMetadata {
        MethodAnnotationMetadata(AnnotationMetadata targetMetadata) {
            super(targetMetadata);
        }

        @Nullable
        @Override
        protected Environment getEnvironment() {
            return environment;
        }
    }
}
