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
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Java port of AbstractClassIntroductionSpec.
 */
class AbstractClassIntroductionTest extends AbstractTypeElementTest {

    private static Object invoke(Object target, String method, Object... args) {
        try {
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                types[i] = args[i] == null ? Object.class : args[i].getClass();
            }
            java.lang.reflect.Method m;
            try {
                // Try declared method first
                m = target.getClass().getDeclaredMethod(method, types);
            } catch (NoSuchMethodException e) {
                // Fallback to public method (may be inherited)
                m = target.getClass().getMethod(method, types);
            }
            if (!m.canAccess(target)) {
                m.setAccessible(true);
            }
            return m.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testNonAbstractMethodDefinedInClassIsNotOverriddenByIntroductionAdvice() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.AbstractBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;

            @Stub
            @jakarta.inject.Singleton
            abstract class AbstractBean {
                public abstract String isAbstract();

                public String nonAbstract() {
                    return "good";
                }
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(0, beanDefinition.getInjectedFields().size());

        try (ApplicationContext context = ApplicationContext.run()) {
            Object instance = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);
            assertNull(invoke(instance, "isAbstract"));
            assertEquals("good", invoke(instance, "nonAbstract"));
        }
    }

    @Test
    void testNonAbstractMethodImplementedFromInterfaceNotOverriddenByIntroductionAdvice() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.AbstractBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;

            interface Foo {
                String nonAbstract();
            }
            @Stub
            @jakarta.inject.Singleton
            abstract class AbstractBean implements Foo {
                public abstract String isAbstract();

                @Override
                public String nonAbstract() {
                    return "good";
                }
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(0, beanDefinition.getInjectedFields().size());

        try (ApplicationContext context = ApplicationContext.run()) {
            Object instance = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);
            assertNull(invoke(instance, "isAbstract"));
            assertEquals("good", invoke(instance, "nonAbstract"));
        }
    }

    @Test
    void testNonAbstractMethodImplementedFromInterfaceWithAdviceOnMethodNotOverridden() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.AbstractBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;

            interface Foo {
                @Stub
                String nonAbstract();
            }
            @Stub
            @jakarta.inject.Singleton
            abstract class AbstractBean implements Foo {
                public abstract String isAbstract();

                @Override
                public String nonAbstract() {
                    return "good";
                }
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(0, beanDefinition.getInjectedFields().size());

        try (ApplicationContext context = ApplicationContext.run()) {
            Object instance = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);
            assertNull(invoke(instance, "isAbstract"));
            assertEquals("good", invoke(instance, "nonAbstract"));
        }
    }

    @Test
    void testNonAbstractMethodImplementedFromSuperInterfaceWithAdviceNotOverridden() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.AbstractBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;

            interface Bar {
                @Stub
                String nonAbstract();

                String another();
            }

            interface Foo extends Bar {
            }

            @Stub
            @jakarta.inject.Singleton
            abstract class AbstractBean implements Foo {
                public abstract String isAbstract();

                @Override
                public String nonAbstract() {
                    return "good";
                }

                @Override
                public String another() {
                    return "good";
                }
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(0, beanDefinition.getInjectedFields().size());

        try (ApplicationContext context = ApplicationContext.run()) {
            Object instance = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);
            assertNull(invoke(instance, "isAbstract"));
            assertEquals("good", invoke(instance, "nonAbstract"));
            assertEquals("good", invoke(instance, "another"));
        }
    }

    @Test
    void testNonAbstractMethodImplementedFromInterfaceWithAdviceOnClassNotOverridden() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.AbstractBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;

            @Stub
            interface Foo {
                String nonAbstract();
            }
            @Stub
            @jakarta.inject.Singleton
            abstract class AbstractBean implements Foo {
                public abstract String isAbstract();

                @Override
                public String nonAbstract() {
                    return "good";
                }
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(0, beanDefinition.getInjectedFields().size());

        try (ApplicationContext context = ApplicationContext.run()) {
            Object instance = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);
            assertNull(invoke(instance, "isAbstract"));
            assertEquals("good", invoke(instance, "nonAbstract"));
        }
    }

    @Test
    void testDefaultMethodDefinedInInterfaceNotImplementedByIntroductionAdvice() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.AbstractBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;

            @Stub
            interface Foo {
                String nonAbstract();

                default String anotherNonAbstract() {
                    return "good";
                }
            }
            @Stub
            @jakarta.inject.Singleton
            abstract class AbstractBean implements Foo {
                public abstract String isAbstract();

                @Override
                public String nonAbstract() {
                    return "good";
                }
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(0, beanDefinition.getInjectedFields().size());

        try (ApplicationContext context = ApplicationContext.run()) {
            Object instance = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);
            assertNull(invoke(instance, "isAbstract"));
            assertEquals("good", invoke(instance, "nonAbstract"));
            assertEquals("good", invoke(instance, "anotherNonAbstract"));
        }
    }

    @Test
    void testDefaultMethodOverriddenFromParentInterfaceNotImplementedByIntroductionAdvice() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.AbstractBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;

            interface Bar {
                String anotherNonAbstract();
            }
            interface Foo extends Bar {
                String nonAbstract();

                @Override
                default String anotherNonAbstract() {
                    return "good";
                }
            }
            @Stub
            @jakarta.inject.Singleton
            abstract class AbstractBean implements Foo {
                public abstract String isAbstract();

                @Override
                public String nonAbstract() {
                    return "good";
                }
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(0, beanDefinition.getInjectedFields().size());

        try (ApplicationContext context = ApplicationContext.run()) {
            Object instance = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);
            assertNull(invoke(instance, "isAbstract"));
            assertEquals("good", invoke(instance, "nonAbstract"));
            assertEquals("good", invoke(instance, "anotherNonAbstract"));
        }
    }
}
