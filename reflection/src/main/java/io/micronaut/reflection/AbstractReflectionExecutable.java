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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.exception.InvocationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GenericPlaceholder;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.type.UnsafeExecutable;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ExceptionUtils;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * The shared shape of the executable methods of this package: the declaring type, the name, the arguments and
 * the return type, with the argument validation, the equality and the description a generated executable method
 * has.
 *
 * @param <T> The declaring type
 * @param <R> The return type
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Internal
abstract class AbstractReflectionExecutable<T, R> implements ExecutableMethod<T, R>, UnsafeExecutable<T, R> {

    private final Class<T> declaringType;
    private final String methodName;
    private final Argument<?>[] arguments;
    private final Class<?>[] argumentTypes;
    private final Argument<R> returnArgument;
    private final ReturnType<R> returnType = new ReflectionReturnType();

    AbstractReflectionExecutable(Class<T> declaringType, String methodName, Argument<R> returnArgument, Argument<?>[] arguments) {
        this.declaringType = declaringType;
        this.methodName = methodName;
        this.returnArgument = returnArgument;
        this.arguments = arguments;
        this.argumentTypes = Argument.toClassArray(arguments);
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
    public Argument<?>[] getArguments() {
        return arguments;
    }

    @Override
    public Class<?>[] getArgumentTypes() {
        return argumentTypes;
    }

    @Override
    public ReturnType<R> getReturnType() {
        return returnType;
    }

    @Override
    @Nullable
    public final R invoke(T instance, @Nullable Object... arguments) {
        if (arguments.length > 0) {
            ArgumentUtils.validateArguments(this, this.arguments, arguments);
        }
        return invokeUnsafe(instance, arguments);
    }

    /**
     * Invokes a method the way a generated dispatcher invokes it: the exception the target throws is the one the
     * caller catches. {@code ReflectionUtils.invokeMethod} wraps it in an {@link InvocationException} instead,
     * which nothing in the framework unwraps, so a retry policy, an exception handler or the catch block of an
     * interceptor would never see the exception the bean method threw.
     *
     * @param method    The method to invoke
     * @param instance  The instance to invoke it on, {@code null} for a static method
     * @param arguments The arguments
     * @return The result the method returns, {@code null} for a method returning nothing
     */
    @Nullable
    static Object invokeTarget(Method method, @Nullable Object instance, @Nullable Object... arguments) {
        try {
            return method.invoke(instance, arguments);
        } catch (InvocationTargetException e) {
            // a generated dispatcher calls the method itself, so the exception of the target leaves the dispatch
            // as it is, a checked one included
            return ExceptionUtils.sneakyThrow(e.getTargetException());
        } catch (IllegalAccessException e) {
            // as `ReflectionUtils.invokeMethod` reports it: the method itself never ran
            throw new InvocationException("Illegal access invoking method [" + method + "]: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractReflectionExecutable<?, ?> that = (AbstractReflectionExecutable<?, ?>) o;
        return declaringType == that.declaringType
            && methodName.equals(that.methodName)
            && Arrays.equals(argumentTypes, that.argumentTypes);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(declaringType, methodName) + Arrays.hashCode(argumentTypes);
    }

    @Override
    public String toString() {
        return returnArgument.getType().getSimpleName() + " " + methodName + "(" + Argument.toString(arguments) + ")";
    }

    /**
     * The return type: as for a generated executable method, its metadata is the metadata of the method, and
     * the argument it yields carries that metadata with the type arguments of the declared return type.
     */
    @Internal
    private final class ReflectionReturnType implements ReturnType<R> {

        @Override
        public Class<R> getType() {
            return returnArgument.getType();
        }

        @Override
        public boolean isSuspended() {
            return AbstractReflectionExecutable.this.isSuspend();
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return AbstractReflectionExecutable.this.getAnnotationMetadata();
        }

        @Override
        public Argument<?>[] getTypeParameters() {
            return returnArgument.getTypeParameters();
        }

        @Override
        public Map<String, Argument<?>> getTypeVariables() {
            return returnArgument.getTypeVariables();
        }

        @Override
        public Argument<R> asArgument() {
            if (returnArgument instanceof GenericPlaceholder<?> placeholder) {
                // a variable stays a variable, as the return argument a generated executable writes is one
                return Argument.ofTypeVariable(getType(), returnArgument.getName(), placeholder.getVariableName(), getAnnotationMetadata(), returnArgument.getTypeParameters());
            }
            return Argument.of(getType(), getAnnotationMetadata(), returnArgument.getTypeParameters());
        }
    }
}
