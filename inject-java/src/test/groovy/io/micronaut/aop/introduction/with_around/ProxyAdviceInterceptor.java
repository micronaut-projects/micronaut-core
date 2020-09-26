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
package io.micronaut.aop.introduction.with_around;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.inject.ExecutableMethod;

import javax.inject.Singleton;

@Singleton
public class ProxyAdviceInterceptor implements MethodInterceptor<Object, Object> {

    private final BeanContext beanContext;

    public ProxyAdviceInterceptor(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        if (context.getMethodName().equalsIgnoreCase("getId")) {
            // Test invocation delegation
            if (context.getTarget() instanceof MyBean5) {
                MyBean5 delegate = new MyBean5();
                delegate.setId(1L);
                return context.getExecutableMethod().invoke(delegate, context.getParameterValues());
            } else if (context.getTarget() instanceof MyBean6) {
                try {
                    ExecutableMethod<MyBean6, Object> proxyTargetMethod = beanContext.getProxyTargetMethod(MyBean6.class, context.getMethodName(), context.getArgumentTypes());
                    MyBean6 delegate = new MyBean6();
                    delegate.setId(1L);
                    return proxyTargetMethod.invoke(delegate, context.getParameterValues());
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            } else {
                return 1L;
            }
        }
        if (context.getMethodName().equalsIgnoreCase("isProxy")) {
            return true;
        }
        return context.proceed();
    }
}
