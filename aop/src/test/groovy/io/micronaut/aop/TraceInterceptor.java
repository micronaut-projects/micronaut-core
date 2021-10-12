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

import io.micronaut.core.type.ArgumentValue;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.inject.MethodExecutionHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * An {@link Interceptor} that adds trace logging around the execution of a method.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class TraceInterceptor implements Interceptor {
    private static final Logger LOG = LoggerFactory.getLogger(TraceInterceptor.class);

    @Override
    public Object intercept(InvocationContext context) {
        if (LOG.isTraceEnabled() && context instanceof MethodExecutionHandle) {
            MethodExecutionHandle handle = (MethodExecutionHandle) context;

            Collection<MutableArgumentValue<?>> values = context.getParameters().values();

            LOG.trace("Invoking method {}#{}(..) with arguments {}",
                    context.getTarget().getClass().getName(), handle.getMethodName(),
                    values.stream().map(ArgumentValue::getValue).collect(Collectors.toList()));
        }
        Object result = context.proceed();
        if (LOG.isTraceEnabled() && context instanceof MethodExecutionHandle) {
            MethodExecutionHandle handle = (MethodExecutionHandle) context;
            LOG.trace("Method {}#{}(..) returned result {}",
                    context.getTarget().getClass().getName(), handle.getMethodName(),
                    result);
        }
        return result;
    }
}