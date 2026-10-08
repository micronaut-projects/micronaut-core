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
package io.micronaut.inject;

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * A reference to a method.
 *
 * @param <T> The type
 * @param <R> The result type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MethodReference<T, R> extends AnnotationMetadataDelegate, AnnotatedElement {

    /**
     * @return The required argument types
     */
    Argument[] getArguments();

    /**
     * The {@link Method} this reference stands for.
     *
     * <p>A reference for which {@link #hasTargetMethod()} is {@code false} stands for no Java method: this returns
     * {@code null} for it despite the declaration, or throws {@link UnsupportedOperationException}. Code that may be
     * handed such a reference, an interceptor of a lifecycle event among them, asks {@link #hasTargetMethod()}
     * first.</p>
     *
     * @return The target method, or {@code null} when {@link #hasTargetMethod()} is {@code false}
     */
    Method getTargetMethod();

    /**
     * Whether this reference stands for a Java {@link Method} that {@link #getTargetMethod()} returns.
     *
     * <p>A reference that stands for no Java method answers {@code false}, and {@link #getTargetMethod()} then
     * returns {@code null} or throws {@link UnsupportedOperationException}. The method invoked for a
     * {@code POST_CONSTRUCT} or {@code PRE_DESTROY} interception of a bean that binds the event without declaring a
     * callback of that kind is one, as is a route to a handler function. A reference that wraps another answers
     * what the wrapped one answers.</p>
     *
     * <p>It answers without looking the method up, so a method that should exist but cannot be found reflectively
     * answers {@code true} and fails as {@link #getTargetMethod()} does.</p>
     *
     * @return Whether there is a target method
     * @since 5.3.0
     */
    @Experimental
    default boolean hasTargetMethod() {
        return true;
    }

    /**
     * @return Return the return type
     */
    ReturnType<R> getReturnType();

    /**
     * @return The bean that declares this injection point
     */
    Class<T> getDeclaringType();

    /**
     * @return The name of the method
     */
    String getMethodName();

    /**
     * @return The argument types
     */
    default Class<?>[] getArgumentTypes() {
        return Arrays
            .stream(getArguments())
            .map(Argument::getType)
            .toArray(Class[]::new);
    }

    /**
     * @return The argument types
     */
    default String[] getArgumentNames() {
        return Arrays
            .stream(getArguments())
            .map(Argument::getName)
            .toArray(String[]::new);
    }

    @Override
    default String getName() {
        return getMethodName();
    }
}
