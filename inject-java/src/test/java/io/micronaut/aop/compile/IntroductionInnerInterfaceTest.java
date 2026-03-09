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

import io.micronaut.aop.Intercepted;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Java port of IntroductionInnerInterfaceSpec.
 */
class IntroductionInnerInterfaceTest extends AbstractTypeElementTest {

    @Test
    void testInnerInterfaceWithIntroductionDoesNotAdviseOuterClass() throws Exception {
        String clsName = "inneritfce.Test";
        ApplicationContext context = buildContext(clsName, """
            package inneritfce;

            import jakarta.inject.*;
            import io.micronaut.aop.introduction.*;

            @Singleton
            class Test {

                @Stub
                interface InnerIntroduction {
                }
            }
            """);
        try {
            Object bean = context.getBean(context.getClassLoader().loadClass(clsName));

            // outer bean is not AOP advice
            assertFalse(bean instanceof Intercepted);

            // proxy not generated for outer type
            assertThrows(ClassNotFoundException.class, () ->
                context.getClassLoader().loadClass(clsName + BeanDefinitionVisitor.PROXY_SUFFIX)
            );
        } finally {
            context.close();
        }
    }
}
