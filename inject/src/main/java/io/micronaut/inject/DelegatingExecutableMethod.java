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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;

import java.lang.reflect.Method;

/**
 * An interface for {@link ExecutableMethod} instances that delegate to an underlying {@link ExecutableMethod}.
 *
 * @param <T> The type
 * @param <R> The result
 * @author Graeme Rocher
 * @since 1.0
 */
public interface DelegatingExecutableMethod<T, R> extends ExecutableMethod<T, R> {

    /**
     * @return The target
     */
    ExecutableMethod<T, R> getTarget();

    @Override
    default Method getTargetMethod() {
        return getTarget().getTargetMethod();
    }

    @Override
    default ReturnType<R> getReturnType() {
        return getTarget().getReturnType();
    }

    @Override
    default Class<T> getDeclaringType() {
        return getTarget().getDeclaringType();
    }

    @Override
    default String getMethodName() {
        return getTarget().getMethodName();
    }

    @Override
    default Class[] getArgumentTypes() {
        return getTarget().getArgumentTypes();
    }

    @Override
    default String[] getArgumentNames() {
        return getTarget().getArgumentNames();
    }

    @Override
    default Argument[] getArguments() {
        return getTarget().getArguments();
    }

    @Override
    default R invoke(T instance, Object... arguments) {
        return getTarget().invoke(instance, arguments);
    }

    @Override
    default AnnotationMetadata getAnnotationMetadata() {
        return getTarget().getAnnotationMetadata();
    }
}
