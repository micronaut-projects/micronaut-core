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

import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.simple.Mutating;
import io.micronaut.aop.writer.AopProxyWriter;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Java port of AnnotatedConstructorArgumentSpec.
 */
class AnnotatedConstructorArgumentTest extends AbstractTypeElementTest {

    private static Object invoke(Object target, String method, Object... args) {
        try {
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                types[i] = args[i] == null ? Object.class : args[i].getClass();
            }
            var m = target.getClass().getDeclaredMethod(method, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testConstructorArgumentsPropagateAnnotationMetadata() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.$MyBean" + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.simple.*;
            import io.micronaut.context.annotation.*;

            @Mutating("someVal")
            @jakarta.inject.Singleton
            class MyBean {

                private String myValue;

                MyBean(@Value("${foo.bar}") String val) {
                    this.myValue = val;
                }

                public String someMethod(String someVal) {
                    return myValue + ' ' + someVal;
                }

                String someMethodPackagePrivateMethod(String someVal) {
                    return myValue + ' ' + someVal;
                }
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(0, beanDefinition.getInjectedFields().size());
        assertEquals(1 + AopProxyWriter.ADDITIONAL_PARAMETERS_COUNT, beanDefinition.getConstructor().getArguments().length);
        assertEquals("val", beanDefinition.getConstructor().getArguments()[0].getName());
        assertEquals("$beanResolutionContext", beanDefinition.getConstructor().getArguments()[1].getName());
        assertEquals("$beanContext", beanDefinition.getConstructor().getArguments()[2].getName());
        assertEquals("$qualifier", beanDefinition.getConstructor().getArguments()[3].getName());
        assertEquals("$interceptors", beanDefinition.getConstructor().getArguments()[4].getName());
        {
            List<AnnotationValue<InterceptorBinding>> bindings =
                beanDefinition.getConstructor().getArguments()[4]
                    .getAnnotationMetadata()
                    .getAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER)
                    .getAnnotations(AnnotationMetadata.VALUE_MEMBER, InterceptorBinding.class);
            assertEquals(Mutating.class.getName(), bindings.get(0).stringValue().orElseThrow());
        }

        try (ApplicationContext context = ApplicationContext.run(Map.of("foo.bar", "test"))) {
            Object instance = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);
            assertEquals("test changed", invoke(instance, "someMethod", "foo"));
            assertEquals("test changed", invoke(instance, "someMethodPackagePrivateMethod", "foo"));
        }
    }

    @Test
    void testConstructorArgumentsPropagateAnnotationMetadataMethodLevelAop() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.$MyBean" + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.simple.*;
            import io.micronaut.context.annotation.*;

            @jakarta.inject.Singleton
            class MyBean {

                private String myValue;

                MyBean(@Value("${foo.bar}") String val) {
                    this.myValue = val;
                }

                @Mutating("someVal")
                public String someMethod(String someVal) {
                    return myValue+ ' ' + someVal;
                }

                @Mutating("someVal")
                String someMethodPackagePrivateMethod(String someVal) {
                    return myValue + ' ' + someVal;
                }
            }
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(0, beanDefinition.getInjectedFields().size());
        assertEquals(1 + AopProxyWriter.ADDITIONAL_PARAMETERS_COUNT, beanDefinition.getConstructor().getArguments().length);
        assertEquals("val", beanDefinition.getConstructor().getArguments()[0].getName());
        assertEquals("$beanResolutionContext", beanDefinition.getConstructor().getArguments()[1].getName());
        assertEquals("$beanContext", beanDefinition.getConstructor().getArguments()[2].getName());
        assertEquals("$qualifier", beanDefinition.getConstructor().getArguments()[3].getName());
        assertEquals("$interceptors", beanDefinition.getConstructor().getArguments()[4].getName());
        {
            List<AnnotationValue<InterceptorBinding>> bindings =
                beanDefinition.getConstructor().getArguments()[4]
                    .getAnnotationMetadata()
                    .getAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER)
                    .getAnnotations(AnnotationMetadata.VALUE_MEMBER, InterceptorBinding.class);
            assertEquals(Mutating.class.getName(), bindings.get(0).stringValue().orElseThrow());
        }

        try (ApplicationContext context = ApplicationContext.run(Map.of("foo.bar", "test"))) {
            Object instance = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);
            assertEquals("test changed", invoke(instance, "someMethod", "foo"));
            assertEquals("test changed", invoke(instance, "someMethodPackagePrivateMethod", "foo"));
        }
    }
}
