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
package io.micronaut.annotation.mapping;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Inherited;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of TransformToInheritedAnnotationSpec.
 */
class TransformToInheritedAnnotationTest extends AbstractTypeElementTest {

    @Test
    void testTransforming() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.TransformToInherited", """
            package addann;

            import io.micronaut.context.annotation.Bean;
            import io.micronaut.context.annotation.Executable;

            interface MyInterfaceX {

                @io.micronaut.annotation.mapping.MyGet2
                @Executable
                String getHelloWorld();
            }

            @Bean
            class TransformToInherited implements MyInterfaceX {

                @Override
                public String getHelloWorld() {
                    return "Hello world";
                }
            }
            """);

        var method = definition.getRequiredMethod("getHelloWorld");
        // Expect the method retains the MyGet2 annotation after transformation (with added stereotype)
        assertTrue(method.hasAnnotation("io.micronaut.annotation.mapping.MyGet2"));
    }

    public static final class TheAnnotationTransformer implements TypedAnnotationTransformer<MyGet2> {

        @Override
        public Class<MyGet2> annotationType() {
            return MyGet2.class;
        }

        @Override
        public List<AnnotationValue<?>> transform(AnnotationValue<MyGet2> annotation, VisitorContext visitorContext) {
            return List.of(
                annotation.mutate().stereotype(
                    AnnotationValue.builder(Inherited.class).build()
                ).build()
            );
        }
    }
}
