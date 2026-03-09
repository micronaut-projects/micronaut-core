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
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.HttpMethodMapping;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of TransformNotInheritedAnnotationSpec.
 */
class TransformNotInheritedAnnotationTest extends AbstractTypeElementTest {

    @Test
    void testTransforming() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.TransformNotInherited", """
            package addann;

            import io.micronaut.context.annotation.Bean;

            interface MyInterface {

                @io.micronaut.annotation.mapping.MyGet1
                String getHelloWorld();
            }

            @Bean
            class TransformNotInherited implements MyInterface {

                @Override
                public String getHelloWorld() {
                    return "Hello world";
                }
            }
            """);

        var method = definition.getRequiredMethod("getHelloWorld");
        assertTrue(method.hasAnnotation(Get.class));
        assertTrue(method.hasStereotype(HttpMethodMapping.class));
    }

    public static final class TheAnnotationTransformer implements TypedAnnotationTransformer<MyGet1> {

        @Override
        public Class<MyGet1> annotationType() {
            return MyGet1.class;
        }

        @Override
        public List<AnnotationValue<?>> transform(AnnotationValue<MyGet1> annotation, VisitorContext visitorContext) {
            return List.of(AnnotationValue.builder(Get.class).build());
        }
    }
}
