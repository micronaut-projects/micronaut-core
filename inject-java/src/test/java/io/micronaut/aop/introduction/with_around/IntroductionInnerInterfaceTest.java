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
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Java port of IntroductionInnerInterfaceSpec (Spock) to JUnit 5.
 */
class IntroductionInnerInterfaceTest extends AbstractTypeElementTest {

    @Test
    void testInnerClassPassedToIntroductionInterfaces() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.aop.*;
            import io.micronaut.context.annotation.*;
            import jakarta.inject.Singleton;
            import java.lang.annotation.Retention;
            import static java.lang.annotation.RetentionPolicy.RUNTIME;

            @Around
            @Type(io.micronaut.aop.introduction.with_around.ObservableInterceptor.class)
            @Introduction(interfaces = ObservableUI.Inner.class)
            @Retention(RUNTIME)
            @interface ObservableUI {
                public interface Inner {
                    String hello();
                }
            }

            @Singleton
            @ObservableUI
            class MyBean {
            }
            """);

        assertNotNull(beanDefinition);

        try (ApplicationContext context = ApplicationContext.run()) {
            Object instance = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);
            @SuppressWarnings("unchecked")
            ExecutableMethod<Object, Object> hello = (ExecutableMethod<Object, Object>) beanDefinition.getRequiredMethod("hello");
            assertEquals("World", hello.invoke(instance));
        }
    }
}
