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
package io.micronaut.inject;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;

/**
 * <p>Represents a handle to an executable object. Differs from {@link io.micronaut.core.type.Executable} in that the
 * first argument to {@link #invoke(Object...)} is not the object instead the object is typically held within the
 * handle itself.</p>
 * <p>
 * <p>Executable handles are also applicable to constructors and static methods</p>
 *
 * @param <T> The target type
 * @param <R> The result type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ExecutionHandle<T, R> extends AnnotationMetadataDelegate {

    /**
     * The target of the method invocation.
     *
     * @return The target object
     */
    T getTarget();

    /**
     * @return The declaring type
     */
    Class getDeclaringType();

    /**
     * @return The required argument types.
     */
    Argument[] getArguments();

    /**
     * Invokes the method.
     *
     * @param arguments The arguments
     * @return The result
     */
    R invoke(Object... arguments);

    /**
     * Creates an {@link ExecutionHandle} for the give bean and method.
     *
     * @param bean The bean
     * @param method The method
     * @param <T2> The bean type
     * @param <R2> The method return type
     * @return The execution handle
     */
    static <T2, R2> MethodExecutionHandle<T2, R2> of(T2 bean, ExecutableMethod<T2, R2> method) {
        return new MethodExecutionHandle<T2, R2>() {
            @Nonnull
            @Override
            public ExecutableMethod<?, R2> getExecutableMethod() {
                return method;
            }

            @Override
            public T2 getTarget() {
                return bean;
            }

            @Override
            public Class getDeclaringType() {
                return bean.getClass();
            }

            @Override
            public String getMethodName() {
                return method.getMethodName();
            }

            @Override
            public Argument[] getArguments() {
                return method.getArguments();
            }

            @Override
            public Method getTargetMethod() {
                return method.getTargetMethod();
            }

            @Override
            public ReturnType getReturnType() {
                return method.getReturnType();
            }

            @Override
            public R2 invoke(Object... arguments) {
                return method.invoke(bean, arguments);
            }

            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                return method.getAnnotationMetadata();
            }
        };
    }
}
