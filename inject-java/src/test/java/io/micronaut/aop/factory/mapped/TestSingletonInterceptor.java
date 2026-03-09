/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.aop.factory.mapped;

import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Test singleton interceptor used in factory mapped advice tests.
 * When a factory method is annotated (via mapped annotation) the interceptor returns the
 * singleton bean from the ApplicationContext instead of invoking the method body.
 */
@Singleton
@InterceptorBean(TestSingletonAdvice.class)
public class TestSingletonInterceptor implements MethodInterceptor<Object, Object> {

    private final ApplicationContext applicationContext;
    private final java.util.concurrent.ConcurrentHashMap<Class<?>, Object> cache = new java.util.concurrent.ConcurrentHashMap<>();

    @Inject
    public TestSingletonInterceptor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        // Cache the result of the factory method to ensure subsequent calls return the same instance
        Class<?> returnType = context.getReturnType().getType();
        return cache.computeIfAbsent(returnType, rt -> context.proceed());
    }
}
