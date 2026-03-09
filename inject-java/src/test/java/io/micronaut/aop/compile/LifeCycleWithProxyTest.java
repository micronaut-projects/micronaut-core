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
package io.micronaut.aop.compile;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Java port of LifeCycleWithProxySpec.
 */
class LifeCycleWithProxyTest extends AbstractTypeElementTest {

    // Reflection helpers
    private static Object getPublicField(Object obj, String field) {
        Object v = ReflectionUtils.getField(obj.getClass(), field, obj);
        return v;
    }

    private static int getPublicIntField(Object obj, String field) {
        Object v = ReflectionUtils.getField(obj.getClass(), field, obj);
        return (v instanceof Number n) ? n.intValue() : Integer.parseInt(String.valueOf(v));
    }

    @Test
    void testProxyTargetAopDefinitionLifecycleHooksInvoked_annotationAtClassLevel() throws Exception {
        try (ApplicationContext context = buildContext("""
            package test;

            import io.micronaut.aop.proxytarget.*;
            import io.micronaut.context.annotation.*;
            import io.micronaut.core.convert.*;

            @Mutating("someVal")
            @jakarta.inject.Singleton
            class MyBean {

                @jakarta.inject.Inject public ConversionService conversionService;
                public int count = 0;

                public String someMethod() {
                    return "good";
                }

                @jakarta.annotation.PostConstruct
                void created() {
                    count++;
                }

                @javax.annotation.PreDestroy
                void destroyed() {
                    count--;
                }
            }
            """)) {
            Object instance = getBean(context, "test.MyBean");

            // proxy post construct methods are not invoked
            assertNotNull(getPublicField(instance, "conversionService"));
            assertEquals("good", ReflectionUtils.invokeInaccessibleMethod(instance, "someMethod"));
            assertEquals(0, getPublicIntField(instance, "count"));

            // proxy target post construct methods are invoked
            Object target = ReflectionUtils.invokeInaccessibleMethod(instance, "interceptedTarget");
            assertEquals(1, getPublicIntField(target, "count"));
        }
    }

    @Test
    void testSimpleAopDefinitionLifecycleHooksInvoked_annotationAtClassLevel() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.$MyBean" + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.simple.*;
            import io.micronaut.context.annotation.*;
            import io.micronaut.core.convert.*;

            @Mutating("someVal")
            @jakarta.inject.Singleton
            class MyBean {

                @jakarta.inject.Inject public ConversionService conversionService;
                public int count = 0;

                public String someMethod() {
                    return "good";
                }

                @jakarta.annotation.PostConstruct
                void created() {
                    count++;
                }

                @javax.annotation.PreDestroy
                void destroyed() {
                    count--;
                }
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(1, beanDefinition.getPostConstructMethods().size());
        assertEquals(1, beanDefinition.getPreDestroyMethods().size());

        try (ApplicationContext context = ApplicationContext.run()) {
            Object instance = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);

            assertNotNull(getPublicField(instance, "conversionService"));
            assertDoesNotThrow(() -> ReflectionUtils.invokeInaccessibleMethod(instance, "someMethod"));
            assertEquals("good", ReflectionUtils.<String, Object>invokeInaccessibleMethod(instance, "someMethod"));
            assertEquals(1, getPublicIntField(instance, "count"));
        }
    }

    @Test
    void testSimpleAopDefinitionLifecycleHooksInvoked_annotationAtMethodLevelWithHooksLast() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.$MyBean" + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.simple.*;
            import io.micronaut.core.convert.*;

            @jakarta.inject.Singleton
            class MyBean {

                @jakarta.inject.Inject public ConversionService conversionService;

                public int count = 0;

                @Mutating("someVal")
                public String someMethod() {
                    return "good";
                }

                @jakarta.annotation.PostConstruct
                void created() {
                    count++;
                }

                @javax.annotation.PreDestroy
                void destroyed() {
                    count--;
                }
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(1, beanDefinition.getPostConstructMethods().size());
        assertEquals(1, beanDefinition.getPreDestroyMethods().size());

        try (ApplicationContext context = ApplicationContext.run()) {
            Object instance = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);

            assertNotNull(getPublicField(instance, "conversionService"));
            assertEquals("good", ReflectionUtils.<String, Object>invokeInaccessibleMethod(instance, "someMethod"));
            assertEquals(1, (int) getPublicIntField(instance, "count"));
        }
    }

    @Test
    void testSimpleAopDefinitionLifecycleHooksInvoked_annotationAtMethodLevel() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.$MyBean" + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.simple.*;
            import io.micronaut.context.annotation.*;
            import io.micronaut.core.convert.*;

            @jakarta.inject.Singleton
            class MyBean {

                @jakarta.inject.Inject public ConversionService conversionService;
                public int count = 0;

                @jakarta.annotation.PostConstruct
                void created() {
                    count++;
                }

                @javax.annotation.PreDestroy
                void destroyed() {
                    count--;
                }

                @Mutating("someVal")
                public String someMethod() {
                    return "good";
                }
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());

        try (ApplicationContext context = ApplicationContext.run()) {
            Object instance = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);

            assertNotNull(getPublicField(instance, "conversionService"));
            assertEquals("good", ReflectionUtils.<String, Object>invokeInaccessibleMethod(instance, "someMethod"));
            assertEquals(1, (int) getPublicIntField(instance, "count"));
        }
    }
}
