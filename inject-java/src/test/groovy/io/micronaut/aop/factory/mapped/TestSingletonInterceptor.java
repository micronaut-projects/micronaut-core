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
package io.micronaut.aop.factory.mapped;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.exceptions.BeanContextException;
import io.micronaut.inject.ExecutableMethod;

import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class TestSingletonInterceptor implements MethodInterceptor<Object, Object> {

    private final Map<ExecutableMethod, Object> computedSingletons = new ConcurrentHashMap<>(30);

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {

        final ExecutableMethod<Object, Object> method = context.getExecutableMethod();
        synchronized (computedSingletons) {
            Object o = computedSingletons.get(method);
            if (o == null) {
                o = context.proceed();
                if (o == null) {
                    throw new BeanContextException("Bean factor method [" + method + "] returned null");
                }
                computedSingletons.put(method, o);
            }
            return o;
        }
    }

}
