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
package io.micronaut.retry;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * The method that the logs and the events of a programmatic circuit breaker name.
 *
 * @param name The name of the circuit breaker
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record ProgrammaticExecutableMethod(String name) implements ExecutableMethod<Object, Object> {

    @Override
    public Class<Object> getDeclaringType() {
        return Object.class;
    }

    @Override
    public String getMethodName() {
        return name;
    }

    @Override
    public Argument<?>[] getArguments() {
        return Argument.ZERO_ARGUMENTS;
    }

    @Override
    public Method getTargetMethod() {
        throw new UnsupportedOperationException("No target method for the programmatic circuit breaker [" + name + "]");
    }

    @Override
    public boolean hasTargetMethod() {
        return false;
    }

    @Override
    public ReturnType<Object> getReturnType() {
        return ReturnType.of(Object.class);
    }

    @Override
    public Object invoke(@Nullable Object instance, Object... arguments) {
        throw new UnsupportedOperationException("No invocation for the programmatic circuit breaker [" + name + "]");
    }

    @Override
    public String toString() {
        return "CircuitBreaker(" + name + ")";
    }
}
