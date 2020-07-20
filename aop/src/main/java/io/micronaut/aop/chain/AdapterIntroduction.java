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
package io.micronaut.aop.chain;

import io.micronaut.aop.Adapter;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ExecutionHandle;
import io.micronaut.inject.qualifiers.Qualifiers;

import static io.micronaut.aop.Adapter.InternalAttributes.*;

/**
 * Internal class that implements introduction advice for the {@link io.micronaut.aop.Adapter} annotation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
final class AdapterIntroduction implements MethodInterceptor<Object, Object> {

    private final ExecutionHandle<?, ?> executionHandle;

    /**
     * Default constructor.
     *
     * @param beanContext The bean context
     * @param method The target method
     */
    AdapterIntroduction(BeanContext beanContext, ExecutableMethod<?, ?> method) {
        Class<?> beanType = method.classValue(Adapter.class, ADAPTED_BEAN).orElse(null);

        if (beanType == null) {
            throw new IllegalStateException("No bean type to adapt found in Adapter configuration for method: " + method);
        }

        String beanMethod  = method.stringValue(Adapter.class, ADAPTED_METHOD).orElse(null);
        if (StringUtils.isEmpty(beanMethod)) {
            throw new IllegalStateException("No bean method to adapt found in Adapter configuration for method: " + method);
        }

        String beanQualifier  = method.stringValue(Adapter.class, ADAPTED_QUALIFIER).orElse(null);
        Class[] argumentTypes = method.classValues(Adapter.class, ADAPTED_ARGUMENT_TYPES);
        Class[] methodArgumentTypes = method.getArgumentTypes();
        if (StringUtils.isNotEmpty(beanQualifier)) {
            this.executionHandle = beanContext.findExecutionHandle(
                    beanType,
                    Qualifiers.byName(beanQualifier),
                    beanMethod,
                    argumentTypes.length == methodArgumentTypes.length ? argumentTypes : methodArgumentTypes
            ).orElseThrow(() -> new IllegalStateException("Cannot adapt method [" + method + "]. Target method [" + beanMethod + "] not found on bean " + beanType));

        } else {
            this.executionHandle = beanContext.findExecutionHandle(
                    beanType,
                    beanMethod,
                    argumentTypes.length == methodArgumentTypes.length  ? argumentTypes : methodArgumentTypes
            ).orElseThrow(() -> new IllegalStateException("Cannot adapt method [" + method + "]. Target method [" + beanMethod + "] not found on bean " + beanType));
        }
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return executionHandle.invoke(context.getParameterValues());
    }
}
