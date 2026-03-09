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

import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of InjectFieldAbstractIntroductionSpec.
 */
class InjectFieldAbstractIntroductionTest extends AbstractTypeElementTest {

    @Test
    void testFieldInjectionOnAbstractClassWithIntroductionAdvice() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.AbstractBean" + BeanDefinitionVisitor.PROXY_SUFFIX, """
            package test;

            import io.micronaut.aop.introduction.*;
            import io.micronaut.context.annotation.*;
            import jakarta.inject.*;

            @Stub
            @Singleton
            abstract class AbstractBean {
                protected @Value("something") String foo;
                protected @Inject SomeOther someOther;

                @Inject public void setFoo(SomeOther foo) {}
                @Inject public void setValue(@Value("something") String val) {}
                public abstract String isAbstract();

                @io.micronaut.context.annotation.Executable
                public String nonAbstract() {
                    return "good";
                }
            }

            class SomeOther {}
            """);

        assertNotNull(beanDefinition);
        assertFalse(beanDefinition.isAbstract());
        assertEquals(2, beanDefinition.getInjectedFields().size());
        assertEquals(2, beanDefinition.getInjectedMethods().size());
        assertTrue(beanDefinition.findMethod("nonAbstract").isPresent());
        // Ensure method dispatch is not reflective
        assertFalse(beanDefinition.findMethod("nonAbstract").get().getClass().getName().contains("ReflectionExecutableMethod"));
    }
}
