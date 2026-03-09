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
package io.micronaut.aop.introduction.with_around;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.test.AbstractTypeElementTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of IntroductionWithAroundOnConcreteClassSpec (Spock) to JUnit 5.
 */
class IntroductionWithAroundOnConcreteClassTest extends AbstractTypeElementTest {

    @Test
    void testIntroductionWithAroundCompile() {
        try (var context = buildContext("aroundwithintro.Test", """
            package aroundwithintro;

            import io.micronaut.aop.introduction.with_around.ProxyIntroductionAndAroundOneAnnotation;

            @ProxyIntroductionAndAroundOneAnnotation
            class Test{}
            """, true)) {
            assertNotNull(getBean(context, "aroundwithintro.Test"));
        }
    }

    @Test
    void testIntroductionWithAroundForVariousClasses() throws Exception {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            Class<?>[] classes = new Class<?>[]{
                MyBean1.class, MyBean2.class, MyBean3.class, MyBean4.class, MyBean5.class, MyBean6.class
            };
            for (Class<?> clazz : classes) {
                BeanDefinition<?> beanDefinition = applicationContext.getBeanDefinition(clazz);
                Object bean = applicationContext.getBean(beanDefinition);

                assertTrue(bean instanceof CustomProxy);
                assertTrue(((CustomProxy) bean).isProxy());
                Object id1 = clazz.getMethod("getId").invoke(bean);
                Object name1 = clazz.getMethod("getName").invoke(bean);
                assertEquals(1L, ((Number) id1).longValue());
                assertNull(name1);

                bean = applicationContext.getBean(clazz);
                assertTrue(bean instanceof CustomProxy);
                assertTrue(((CustomProxy) bean).isProxy());
                Object id2 = clazz.getMethod("getId").invoke(bean);
                Object name2 = clazz.getMethod("getName").invoke(bean);
                assertEquals(1L, ((Number) id2).longValue());
                assertNull(name2);

                assertEquals(5, beanDefinition.getExecutableMethods().size());
            }
        }
    }

    @Test
    void testIntrospectedPresent() {
        assertNotNull(BeanIntrospection.getIntrospection(MyBean4.class));
        assertNotNull(BeanIntrospection.getIntrospection(MyBean5.class));
        assertNotNull(BeanIntrospection.getIntrospection(MyBean6.class));
    }

    @Test
    void testExecutableMethodsCountForIntroductionWithExecutable() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            BeanDefinition<?> beanDefinition = applicationContext.getBeanDefinition(MyBean7.class);
            assertEquals(5, beanDefinition.getExecutableMethods().size());
        }
    }

    @Test
    void testExecutableMethodsCountForAroundWithExecutable() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            BeanDefinition<?> beanDefinition = applicationContext.getBeanDefinition(MyBean8.class);
            assertEquals(4, beanDefinition.getExecutableMethods().size());

            MyBean8 myBean8 = applicationContext.getBean(MyBean8.class);
            assertEquals(1L, myBean8.getId());
        }
    }

    @Test
    void testMultidimensionalArrayProperty() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            BeanDefinition<?> beanDefinition = applicationContext.getBeanDefinition(MyBean9.class);
            assertEquals(5, beanDefinition.getExecutableMethods().size());
            assertEquals(String[][].class, beanDefinition.findMethod("getMultidim").get().getReturnType().asArgument().getType());
            assertTrue(beanDefinition.findMethod("setMultidim", String[][].class).isPresent());
            assertEquals(int[][].class, beanDefinition.findMethod("getPrimitiveMultidim").get().getReturnType().asArgument().getType());
            assertTrue(beanDefinition.findMethod("setPrimitiveMultidim", int[][].class).isPresent());

            MyBean9 bean = applicationContext.getBean(MyBean9.class);
            ExecutableMethod getMultiDim = beanDefinition.findMethod("getMultidim").get();
            ExecutableMethod setMultiDim = beanDefinition.findMethod("setMultidim", String[][].class).get();
            ExecutableMethod getPrimitiveMultidim = beanDefinition.findMethod("getPrimitiveMultidim").get();
            ExecutableMethod setPrimitiveMultidim = beanDefinition.findMethod("setPrimitiveMultidim", int[][].class).get();

            assertNull(getMultiDim.invoke(bean));
            assertNull(getPrimitiveMultidim.invoke(bean));

            setMultiDim.invoke(bean, new Object[]{new String[][]{new String[]{"test"}, new String[]{"abc"}}});
            setPrimitiveMultidim.invoke(bean, new Object[]{new int[][]{new int[]{1}, new int[]{2}}});

            assertEquals("test", bean.getMultidim()[0][0]);
            assertEquals(1, bean.getPrimitiveMultidim()[0][0]);
            assertEquals("test", ((String[][]) getMultiDim.invoke(bean))[0][0]);
            assertEquals(1, ((int[][]) getPrimitiveMultidim.invoke(bean))[0][0]);
        }
    }
}
