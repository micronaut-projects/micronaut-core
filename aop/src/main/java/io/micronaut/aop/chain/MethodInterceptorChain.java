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
package io.micronaut.aop.chain;

import io.micronaut.aop.*;
import io.micronaut.aop.exceptions.UnimplementedAdviceException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.ReturnType;
import io.micronaut.inject.ExecutableMethod;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;

/**
 * An internal representation of the {@link Interceptor} chain. This class implements {@link MethodInvocationContext} and is
 * consumed by the framework itself and should not be used directly in application code.
 *
 *
 * @param <T> type
 * @param <R> result
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public final class MethodInterceptorChain<T, R> extends InterceptorChain<T, R> implements MethodInvocationContext<T, R> {

    /**
     * Constructor.
     *
     * @param interceptors array of interceptors
     * @param target target
     * @param executionHandle executionHandle
     * @param originalParameters originalParameters
     */
    public MethodInterceptorChain(Interceptor<T, R>[] interceptors, T target, ExecutableMethod<T, R> executionHandle, Object... originalParameters) {
        super(interceptors, target, executionHandle, originalParameters);
    }

    @Override
    public R proceed() throws RuntimeException {
        Interceptor<T, R> interceptor;
        if (interceptorCount == 0 || index == interceptorCount) {
            if (target instanceof Introduced && executionHandle.isAbstract()) {
                throw new UnimplementedAdviceException(executionHandle);
            } else {
                return executionHandle.invoke(target, getParameterValues());
            }
        } else {
            interceptor = this.interceptors[index++];
            if (LOG.isTraceEnabled()) {
                LOG.trace("Proceeded to next interceptor [{}] in chain for method invocation: {}", interceptor, executionHandle);
            }

            if (interceptor instanceof MethodInterceptor) {
                return ((MethodInterceptor<T, R>) interceptor).intercept(this);
            } else {
                return interceptor.intercept(this);
            }
        }
    }

    @Override
    public String getMethodName() {
        return executionHandle.getMethodName();
    }

    @Override
    public Class[] getArgumentTypes() {
        return executionHandle.getArgumentTypes();
    }

    @Override
    public Method getTargetMethod() {
        return executionHandle.getTargetMethod();
    }

    @Override
    public ReturnType<R> getReturnType() {
        return executionHandle.getReturnType();
    }

    @Override
    public Class<T> getDeclaringType() {
        return executionHandle.getDeclaringType();
    }

    @Override
    public String toString() {
        return executionHandle.toString();
    }

    @Nonnull
    @Override
    public ExecutableMethod<T, R> getExecutableMethod() {
        return executionHandle;
    }
}
