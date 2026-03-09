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
import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of InheritedAnnotationMetadataSpec.
 */
class InheritedAnnotationMetadataTest extends AbstractTypeElementTest {

    @Test
    void testAnnotationMetadataInheritedFromOverriddenMethodsForIntroductionAdvice() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;
            import io.micronaut.core.annotation.*;

            @Stub
            @jakarta.inject.Singleton
            interface MyBean extends MyInterface {

                @Override
                public String someMethod();
            }

            interface MyInterface {
                @Blocking
                @Executable
                public String someMethod();
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(0, beanDefinition.getInjectedFields().size());
        assertEquals(1, beanDefinition.getExecutableMethods().size());
        ExecutableMethod<?, ?> m = beanDefinition.getExecutableMethods().iterator().next();
        assertTrue(m.getAnnotationMetadata().hasAnnotation(Blocking.class));
        assertFalse(m.getAnnotationMetadata().hasDeclaredAnnotation(Blocking.class));
    }

    @Test
    void testAnnotationMetadataInheritedFromOverriddenMethodsForAroundAdvice() throws Exception {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.$MyBean" + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.simple.*;
            import io.micronaut.context.annotation.*;
            import io.micronaut.core.annotation.*;

            @Mutating("someVal")
            @jakarta.inject.Singleton
            class MyBean implements MyInterface {

                private String myValue;

                MyBean(@Value("${foo.bar}") String val) {
                    this.myValue = val;
                }

                @Override
                public String someMethod() {
                    return myValue;
                }
            }

            interface MyInterface {
                @Blocking
                @Executable
                public String someMethod();
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(0, beanDefinition.getInjectedFields().size());
        assertEquals(1, beanDefinition.getExecutableMethods().size());
        ExecutableMethod<?, ?> m2 = beanDefinition.getExecutableMethods().iterator().next();
        assertTrue(m2.getAnnotationMetadata().hasAnnotation(Blocking.class));

        try (ApplicationContext context = ApplicationContext.run(java.util.Map.of("foo.bar", "test"))) {
            Object instance = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);
            assertEquals("test", ReflectionUtils.invokeInaccessibleMethod(instance, "someMethod"));
        }
    }

    @Test
    void testBeanDefinitionIsNotCreatedForAbstractClass() throws Exception {
        ApplicationContext ctx = buildContext("test.$Service" + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.*;
            import io.micronaut.context.annotation.*;
            import io.micronaut.core.annotation.*;
            import io.micronaut.core.order.Ordered;
            import java.lang.annotation.*;
            import jakarta.inject.Singleton;

            interface ContractService {
                @SomeAnnot
                void interfaceServiceMethod();
            }

            abstract class BaseService {
                @SomeAnnot
                public void baseServiceMethod() {}
            }

            @SomeAnnot
            abstract class BaseAnnotatedService {
            }

            @Singleton
            class Service extends BaseService implements ContractService {

                @SomeAnnot
                public void serviceMethod() {}

                public void interfaceServiceMethod() {}
            }

            @Documented
            @Retention(RetentionPolicy.RUNTIME)
            @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
            @Around
            @Type(SomeInterceptor.class)
            @interface SomeAnnot {}

            @Singleton
            class SomeInterceptor implements MethodInterceptor<Object, Object>, Ordered {

                @Override
                public Object intercept(MethodInvocationContext<Object, Object> context) {
                    return context.proceed();
                }
            }
            """);

        // ContractService should be a bean (via Service)
        Class<?> contract = ctx.getClassLoader().loadClass("test.ContractService");
        assertNotNull(ctx.getBean(contract));

        // No bean definitions should be created for abstract classes
        assertThrows(ClassNotFoundException.class, () ->
            ctx.getClassLoader().loadClass("test.$BaseService" + BeanDefinitionWriter.CLASS_SUFFIX)
        );
        assertThrows(ClassNotFoundException.class, () ->
            ctx.getClassLoader().loadClass("test.$BaseService" + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX)
        );
        assertThrows(ClassNotFoundException.class, () ->
            ctx.getClassLoader().loadClass("test.$BaseAnnotatedService" + BeanDefinitionWriter.CLASS_SUFFIX)
        );
    }
}
