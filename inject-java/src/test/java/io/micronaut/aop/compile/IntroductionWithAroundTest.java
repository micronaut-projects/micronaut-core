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
import io.micronaut.core.reflect.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Java port of IntroductionWithAroundSpec.
 */
class IntroductionWithAroundTest extends AbstractTypeElementTest {

    @Test
    void testAroundAdviceAppliedToIntroductionConcreteMethods() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.MyBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;
            import java.net.*;
            import jakarta.validation.constraints.*;
            import jakarta.inject.Singleton;

            @Stub
            @Singleton
            abstract class MyBean {
                abstract void save(@NotBlank String name, @Min(1L) int age);
                abstract void saveTwo(@Min(1L) String name);

                @io.micronaut.aop.simple.Mutating("name")
                public String myConcrete(String name) {
                    return name;
                }
            }
            """);

        assertNotNull(beanDefinition);

        try (ApplicationContext context = ApplicationContext.run()) {
            Object instance = ((InstantiatableBeanDefinition<?>) beanDefinition).instantiate(context);
            assertEquals("changed", ReflectionUtils.invokeInaccessibleMethod(instance, "myConcrete", "test"));
        }
    }
}
