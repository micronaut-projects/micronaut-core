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

import io.micronaut.aop.exceptions.UnimplementedAdviceException;
import io.micronaut.aop.introduction.NotImplementedAdvice;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of IntroductionAnnotationSpec.
 */
class IntroductionAnnotationTest extends AbstractTypeElementTest {

    private static Object invoke(Object bean, String name, Object... args) {
        try {
            Class<?>[] argTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                Object a = args[i];
                if (a == null) {
                    argTypes[i] = Object.class;
                } else if (a instanceof Integer) {
                    argTypes[i] = int.class;
                } else if (a instanceof Short) {
                    argTypes[i] = short.class;
                } else if (a instanceof Byte) {
                    argTypes[i] = byte.class;
                } else if (a instanceof Long) {
                    argTypes[i] = long.class;
                } else if (a instanceof Float) {
                    argTypes[i] = float.class;
                } else if (a instanceof Double) {
                    argTypes[i] = double.class;
                } else if (a instanceof Boolean) {
                    argTypes[i] = boolean.class;
                } else if (a instanceof Character) {
                    argTypes[i] = char.class;
                } else if (a instanceof byte[]) {
                    argTypes[i] = byte[].class;
                } else {
                    argTypes[i] = a.getClass();
                }
            }
            try {
                var m = bean.getClass().getMethod(name, argTypes);
                m.setAccessible(true);
                return m.invoke(bean, args);
            } catch (NoSuchMethodException e) {
                var m = bean.getClass().getDeclaredMethod(name, argTypes);
                m.setAccessible(true);
                return m.invoke(bean, args);
            }
        } catch (java.lang.reflect.InvocationTargetException ite) {
            Throwable cause = ite.getTargetException();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testUnimplementedIntroductionAdvice() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;

            @NotImplemented
            interface MyBean {
                void test();
            }
            """);

        try (ApplicationContext context = ApplicationContext.run()) {
            Object bean = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);

            assertInstanceOf(AdvisedBeanType.class, beanDefinition);
            assertEquals("test.MyBean", ((AdvisedBeanType) beanDefinition).getInterceptedType().getName());

            assertThrows(UnimplementedAdviceException.class, () -> invoke(bean, "test"));
        }
    }

    @Test
    void testUnimplementedIntroductionAdviceOnAbstractClassWithConcreteMethods() throws Exception {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;
            import io.micronaut.aop.simple.Mutating;

            @NotImplemented
            abstract class MyBean {
                abstract void test();

                public String test2() {
                    return "good";
                }

                @Mutating("arg")
                public String test3(String arg) {
                    return arg;
                }
            }
            """);

        try (ApplicationContext context = ApplicationContext.run()) {
            Object bean = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);

            assertThrows(UnimplementedAdviceException.class, () -> invoke(bean, "test"));
            assertTrue(NotImplementedAdvice.invoked);

            NotImplementedAdvice.invoked = false;

            assertEquals("good", invoke(bean, "test2"));
            // Mutating advice applied to parameter "arg" returns "changed"
            assertEquals("changed", invoke(bean, "test3", "x"));
            assertFalse(NotImplementedAdvice.invoked);
        }
    }

    @Test
    void testMinAnnotationOnIntroducedMethods() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;
            import java.net.*;
            import jakarta.validation.constraints.*;

            interface MyInterface{
                @Executable
                void save(@NotBlank String name, @Min(1L) int age);
                @Executable
                void saveTwo(@Min(1L) String name);
            }

            @Stub
            @jakarta.inject.Singleton
            interface MyBean extends MyInterface {
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(0, beanDefinition.getInjectedFields().size());
        assertEquals(2, beanDefinition.getExecutableMethods().size());

        var saveMethod = beanDefinition.getExecutableMethods().stream()
            .filter(m -> m.getMethodName().equals("save"))
            .findFirst().orElseThrow();

        assertEquals("save", saveMethod.getMethodName());
        assertEquals(void.class, saveMethod.getReturnType().getType());
        assertTrue(saveMethod.getArguments()[0].getAnnotationMetadata().hasAnnotation(NotBlank.class));
        assertTrue(saveMethod.getArguments()[1].getAnnotationMetadata().hasAnnotation(Min.class));
        assertEquals(1, saveMethod.getArguments()[1].getAnnotationMetadata().getValue(Min.class, Integer.class).orElseThrow());

        var saveTwoMethod = beanDefinition.getExecutableMethods().stream()
            .filter(m -> m.getMethodName().equals("saveTwo"))
            .findFirst().orElseThrow();

        assertEquals("saveTwo", saveTwoMethod.getMethodName());
        assertEquals(void.class, saveTwoMethod.getReturnType().getType());
        assertTrue(saveTwoMethod.getArguments()[0].getAnnotationMetadata().hasAnnotation(Min.class));
    }
}
