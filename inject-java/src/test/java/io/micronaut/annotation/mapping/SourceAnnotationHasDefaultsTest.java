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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of SourceAnnotationHasDefaultsSpec.
 */
class SourceAnnotationHasDefaultsTest extends AbstractTypeElementTest {

    @Override
    protected java.util.Collection<TypeElementVisitor<?, ?>> getLocalTypeElementVisitors() {
        return java.util.List.of(new TheVisitor());
    }

    @Test
    void testSourceAnnotationHasDefaults() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.SourceDefaultsAnnotationTest", """
            package addann;

            import io.micronaut.inject.annotation.ScopeOne;
            import io.micronaut.context.annotation.Bean;

            @io.micronaut.annotation.mapping.MySourceAnnotation
            @Bean
            class SourceDefaultsAnnotationTest {
            }
            """);

        assertTrue(definition.hasAnnotation("io.micronaut.annotation.mapping.Seen"));
    }

    static final class TheVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        public void visitClass(ClassElement element, VisitorContext context) {
            if ("SourceDefaultsAnnotationTest".equals(element.getSimpleName())) {
                var annotation = element.getAnnotation("io.micronaut.annotation.mapping.MySourceAnnotation");
                String propertyValue = annotation.getRequiredValue("property", String.class);
                Integer countValue = annotation.getRequiredValue("count", Integer.class);
                if (!"foo".equals(propertyValue) || countValue == null || countValue != 123) {
                    throw new IllegalStateException();
                } else {
                    element.annotate("io.micronaut.annotation.mapping.Seen");
                }
            }
        }
    }
}
