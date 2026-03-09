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
package io.micronaut.aop.factory.mapped;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.annotation.AnnotationMapper;
import io.micronaut.inject.test.AbstractTypeElementTest;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Java port of FactoryMappedAdviceSpec.
 */
class FactoryMappedAdviceTest extends AbstractTypeElementTest {


    @Override
    protected List<AnnotationMapper<? extends Annotation>> getLocalAnnotationMappers(String annotationName) {
        if ("io.micronaut.aop.factory.mapped.TestConfiguration".equals(annotationName)) {
            return List.of(new TestConfigurationMapper());
        }
        return Collections.emptyList();
    }

    @Test
    void testConfigurationMapping() throws Exception {
        try (ApplicationContext applicationContext = buildContext("test.MyConfiguration", """
            package test;

            @io.micronaut.aop.factory.mapped.TestConfiguration
            @io.micronaut.context.annotation.Factory
            public class MyConfiguration {

                @io.micronaut.context.annotation.Bean
                public MyBean myBean() {
                    return new MyBean("default");
                }
            }

            class MyBean {
                private final String name;

                MyBean(String name) {
                    this.name = name;
                }

                public String getName() {
                    return name;
                }
            }
            """, true)) {

            applicationContext.registerSingleton(new TestSingletonInterceptor(applicationContext));

            Class<?> type = applicationContext.getClassLoader().loadClass("test.MyBean");
            Class<?> config = applicationContext.getClassLoader().loadClass("test.MyConfiguration");

            // same bean instance through context
            assertSame(applicationContext.getBean(type), applicationContext.getBean(type));

            // calling factory method multiple times yields same instance (due to mapping + scope)
            Object cfg = applicationContext.getBean(config);
            Object b1 = config.getMethod("myBean").invoke(cfg);
            Object b2 = config.getMethod("myBean").invoke(cfg);
            assertSame(b1, b2);
        }
    }

}
