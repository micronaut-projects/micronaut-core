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
package io.micronaut.aop.internal.intercepted;

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.Interceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;

/**
 * The synchronous method intercept.
 *
 * @author Denis Stepanov
 * @since 2.1.0
 */
@Internal
@Experimental
public class SynchronousInterceptedMethod implements InterceptedMethod {

    private final MethodInvocationContext<?, ?> context;
    private final Argument<?> returnTypeValue;

    SynchronousInterceptedMethod(MethodInvocationContext<?, ?> context) {
        this.context = context;
        this.returnTypeValue = context.getReturnType().asArgument();
    }

    @Override
    public ResultType resultType() {
        return ResultType.SYNCHRONOUS;
    }

    @Override
    public Argument<?> returnTypeValue() {
        return returnTypeValue;
    }

    @Override
    public Object interceptResult() {
        return context.proceed();
    }

    @Override
    public Object interceptResult(Interceptor<?, ?> from) {
        return context.proceed(from);
    }

    @Override
    public Object handleResult(Object result) {
        return result;
    }

    @Override
    public <E extends Throwable> Object handleException(Exception exception) throws E {
        throw (E) exception;
    }

}
