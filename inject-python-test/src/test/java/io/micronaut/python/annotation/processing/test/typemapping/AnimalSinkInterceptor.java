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
package io.micronaut.python.annotation.processing.test.typemapping;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.beans.BeanIntrospection;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the arguments of the intercepted abstract methods, as a message client serializing them would see them.
 */
@Singleton
public class AnimalSinkInterceptor implements MethodInterceptor<Object, Object> {

    private final List<Object> received = new ArrayList<>();

    public List<Object> getReceived() {
        return received;
    }

    /**
     * @param index The argument index
     * @return The introspection generated for the runtime type of the received argument, which is what a
     * serializer looks up; {@code null} when the runtime type has none
     */
    public BeanIntrospection<?> introspectionOf(int index) {
        Object argument = received.get(index);
        Class<?> type = argument.getClass();
        String introspectionName = type.getPackageName() + ".$" + type.getSimpleName() + "$Introspection";
        try {
            return (BeanIntrospection<?>) type.getClassLoader().loadClass(introspectionName).getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        if (context.getExecutableMethod().isAbstract()) {
            received.addAll(List.of(context.getParameterValues()));
            return null;
        }
        return context.proceed();
    }
}
