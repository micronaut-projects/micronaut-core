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
package io.micronaut.context;

import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanContextExecutionTest {

    @Test
    void compatibleLookupSkipsEarlierIncompatibleOverloads() {
        try (BeanContext context = BeanContext.run()) {
            context.registerBeanDefinition(new ServiceDefinition());

            var method = context.findExecutableMethod(Service.class, "accept", String.class).orElseThrow();
            assertEquals(CharSequence.class, method.getArgumentTypes()[0]);
            MethodExecutionHandle<Service, String> handle = context.<Service, String>findExecutionHandle(Service.class, "accept", String.class).orElseThrow();
            assertEquals("managed:value", handle.invoke("value"));

            assertFalse(context.findExecutionHandle(Service.class, "accept", Runnable.class).isPresent());
            assertTrue(context.findExecutableMethod(Service.class, "accept").isPresent());
            assertFalse(context.findExecutionHandle(Service.class, "accept").isPresent());
        }
    }

    @Test
    void exactOverloadTakesPrecedenceOverEarlierCompatibleOverloads() {
        try (BeanContext context = BeanContext.run()) {
            context.registerBeanDefinition(new ServiceDefinition());

            assertEquals(String.class, context.findExecutableMethod(Service.class, "exact", String.class).orElseThrow().getArgumentTypes()[0]);
            assertEquals(String.class, context.findExecutionHandle(Service.class, "exact", String.class).orElseThrow().getArguments()[0].getType());
        }
    }

    @Test
    void instanceHandleInvokesTheSuppliedInstance() {
        try (BeanContext context = BeanContext.run()) {
            context.registerBeanDefinition(new ServiceDefinition());
            Service supplied = new Service("supplied");

            MethodExecutionHandle<Service, String> handle = context.<Service, String>findExecutionHandle(supplied, "accept", String.class).orElseThrow();

            assertSame(supplied, handle.getTarget());
            assertEquals("supplied:value", handle.invoke("value"));
            assertFalse(context.findExecutionHandle(supplied, "accept", Runnable.class).isPresent());
            assertFalse(context.findExecutionHandle(supplied, "accept").isPresent());
        }
    }

    @Test
    void broadDiscoveryBoundsRemainSupportedAfterDirectCompatibilityIsTried() {
        try (BeanContext context = BeanContext.run()) {
            context.registerBeanDefinition(new ServiceDefinition());

            var bounded = context.findExecutionHandle(Service.class, "bounded", CharSequence.class).orElseThrow();
            assertEquals(String.class, bounded.getArguments()[0].getType());
            assertEquals("managed:value", bounded.invoke("value"));
            var direct = context.findExecutionHandle(Service.class, "preferred", CharSequence.class).orElseThrow();
            assertEquals(Object.class, direct.getArguments()[0].getType());
            assertEquals(Integer.class, context.findExecutionHandle(Service.class, "accept", Object.class)
                .orElseThrow().getArguments()[0].getType());
            assertTrue(context.findExecutableMethod(Service.class, "accept", Runnable.class).isPresent());
        }
    }

    @Test
    void compatibleLookupPrefersTheMostSpecificOverload() {
        try (BeanContext context = BeanContext.run()) {
            context.registerBeanDefinition(new ServiceDefinition());

            assertEquals(CharSequence.class, context.findExecutableMethod(Service.class, "specific", String.class)
                .orElseThrow().getArgumentTypes()[0]);
            MethodExecutionHandle<Service, String> handle = context.<Service, String>findExecutionHandle(Service.class, "specific", String.class).orElseThrow();
            assertEquals(CharSequence.class, handle.getArguments()[0].getType());
            assertEquals("managed:value", handle.invoke("value"));
            assertEquals(CharSequence.class, context.findExecutionHandle(new Service("supplied"), "specific", String.class)
                .orElseThrow().getArguments()[0].getType());
            assertEquals(Object.class, context.findExecutionHandle(Service.class, "specific", Integer.class)
                .orElseThrow().getArguments()[0].getType());
        }
    }

    @Test
    void compatibleLookupPrefersTheMostSpecificMultiParameterOverload() {
        try (BeanContext context = BeanContext.run()) {
            context.registerBeanDefinition(new ServiceDefinition());

            MethodExecutionHandle<Service, String> handle = context.<Service, String>findExecutionHandle(Service.class, "pair", String.class, Integer.class).orElseThrow();
            assertEquals(List.of(CharSequence.class, Number.class), Arrays.stream(handle.getArgumentTypes()).toList());
            assertEquals("managed:value:1", handle.invoke("value", 1));
            assertEquals(List.of(CharSequence.class, Object.class), Arrays.stream(context.findExecutableMethod(Service.class, "pair", String.class, Boolean.class)
                .orElseThrow().getArgumentTypes()).toList());
        }
    }

    @Test
    void incomparableCompatibleOverloadsKeepTheEarliestDeclared() {
        try (BeanContext context = BeanContext.run()) {
            context.registerBeanDefinition(new ServiceDefinition());

            assertEquals(List.of(CharSequence.class, Object.class), Arrays.stream(context.findExecutableMethod(Service.class, "ambiguous", String.class, Integer.class)
                .orElseThrow().getArgumentTypes()).toList());
        }
    }

    @Test
    void plainBeanContextOnlyResolvesInterfacesItImplements() {
        try (BeanContext context = BeanContext.run()) {
            assertSame(context, context.getBean(BeanContext.class));
            assertSame(context, context.getBean(BeanLocator.class));
            assertFalse(context.findBean(ApplicationContext.class).isPresent());
            assertFalse(context.findBean(PropertyResolver.class).isPresent());
            assertThrows(NoSuchBeanException.class, () -> context.getBean(ApplicationContext.class));
            assertThrows(NoSuchBeanException.class, () -> context.getBean(PropertyResolver.class));
        }
        try (ApplicationContext context = ApplicationContext.run()) {
            assertSame(context, context.getBean(ApplicationContext.class));
            assertSame(context, context.getBean(PropertyResolver.class));
        }
    }

    private record Service(String label) {
    }

    private static final class ServiceDefinition implements RuntimeBeanDefinition<Service> {
        private final List<TestMethod> methods = List.of(
            new TestMethod("accept", Integer.class),
            new TestMethod("accept", CharSequence.class),
            new TestMethod("exact", Object.class),
            new TestMethod("exact", String.class),
            new TestMethod("bounded", Integer.class),
            new TestMethod("bounded", String.class),
            new TestMethod("preferred", String.class),
            new TestMethod("preferred", Object.class),
            new TestMethod("specific", Object.class),
            new TestMethod("specific", CharSequence.class),
            new TestMethod("pair", Object.class, Object.class),
            new TestMethod("pair", CharSequence.class, Object.class),
            new TestMethod("pair", Object.class, Number.class),
            new TestMethod("pair", CharSequence.class, Number.class),
            new TestMethod("pair", Object.class, Integer.class, Object.class),
            new TestMethod("ambiguous", CharSequence.class, Object.class),
            new TestMethod("ambiguous", Object.class, Number.class)
        );

        @Override
        public Class<Service> getBeanType() {
            return Service.class;
        }

        @Override
        public String getBeanDefinitionName() {
            return getClass().getName();
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        @Override
        public Service instantiate(BeanResolutionContext resolutionContext, BeanContext context) {
            return new Service("managed");
        }

        @Override
        public <R> Optional<ExecutableMethod<Service, R>> findMethod(String name, Class<?>... argumentTypes) {
            return this.<R>findPossibleMethods(name)
                .filter(method -> Arrays.equals(method.getArgumentTypes(), argumentTypes))
                .findFirst();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R> Stream<ExecutableMethod<Service, R>> findPossibleMethods(String name) {
            return methods.stream()
                .filter(method -> method.getMethodName().equals(name))
                .map(method -> (ExecutableMethod<Service, R>) method);
        }
    }

    private static final class TestMethod implements ExecutableMethod<Service, String> {
        private final String methodName;
        private final Class<?>[] parameterTypes;

        private TestMethod(String methodName, Class<?>... parameterTypes) {
            this.methodName = methodName;
            this.parameterTypes = parameterTypes;
        }

        @Override
        public Class<Service> getDeclaringType() {
            return Service.class;
        }

        @Override
        public String getMethodName() {
            return methodName;
        }

        @Override
        public Argument<?>[] getArguments() {
            return Arrays.stream(parameterTypes).map(Argument::of).toArray(Argument<?>[]::new);
        }

        @Override
        public ReturnType<String> getReturnType() {
            return ReturnType.of(String.class);
        }

        @Override
        public boolean hasTargetMethod() {
            return false;
        }

        @Override
        public Method getTargetMethod() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String invoke(Service instance, @Nullable Object... arguments) {
            var result = new StringBuilder(instance.label());
            for (int i = 0; i < parameterTypes.length; i++) {
                result.append(':').append(parameterTypes[i].cast(arguments[i]));
            }
            return result.toString();
        }
    }
}
