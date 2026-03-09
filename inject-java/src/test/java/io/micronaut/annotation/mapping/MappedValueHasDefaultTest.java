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
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Java port of MappedValueHasDefaultSpec.
 */
class MappedValueHasDefaultTest extends AbstractTypeElementTest {

    @Test
    void testRemapping() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.MappedValueHasDefault", """
            package addann;

            import io.micronaut.context.annotation.Bean;

            @Bean
            @io.micronaut.annotation.mapping.MySourceAnnotation2
            class MappedValueHasDefault {

                public Object myField;
            }
            """);

        // Source-retention annotation should not appear in runtime metadata
        assertFalse(definition.hasAnnotation("io.micronaut.annotation.mapping.MySourceAnnotation2"));

        try {
            @SuppressWarnings("unchecked")
            Class<? extends java.lang.annotation.Annotation> myRequires =
                (Class<? extends java.lang.annotation.Annotation>) definition.getClass().getClassLoader()
                    .loadClass("io.micronaut.annotation.mapping.MyRequires");
            var values = definition.getAnnotationMetadata().getAnnotationValuesByType(myRequires);
            assertEquals(2, values.size());
            Set<String> properties = values.stream()
                .flatMap(av -> av.stringValue("property").stream())
                .collect(Collectors.toSet());
            assertEquals(Set.of("fooX", "barX"), properties);
        } catch (ClassNotFoundException e) {
            fail(e);
        }
    }

    public static final class TheAnnotationMapper implements TypedAnnotationMapper<MySourceAnnotation2> {

        @Override
        public List<AnnotationValue<?>> map(AnnotationValue<MySourceAnnotation2> annotation, VisitorContext visitorContext) {
            String propertyValue = annotation.getRequiredValue("property", String.class);
            Integer countValue = annotation.getRequiredValue("count", Integer.class);
            if (!"foo".equals(propertyValue) || countValue == null || countValue != 123) {
                throw new IllegalStateException();
            }
            return Arrays.asList(
                AnnotationValue.builder("io.micronaut.annotation.mapping.MyRequires").member("property", "fooX").build(),
                AnnotationValue.builder("io.micronaut.annotation.mapping.MyRequires").member("property", "barX").build()
            );
        }

        @Override
        public Class<MySourceAnnotation2> annotationType() {
            return MySourceAnnotation2.class;
        }
    }
}
