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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Java port of TransformsToRepeatableAnnotationSpec.
 */
class TransformsToRepeatableAnnotationTest extends AbstractTypeElementTest {

    @Override
    protected List<io.micronaut.inject.annotation.AnnotationTransformer<? extends java.lang.annotation.Annotation>> getLocalAnnotationTransformers(String annotationName) {
        if ("io.micronaut.annotation.mapping.TransformMeToRepeatable".equals(annotationName)) {
            return List.of(new TheAnnotationTransformer());
        }
        return super.getLocalAnnotationTransformers(annotationName);
    }

    @Test
    void testRemapping() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.TransformToRepeatableAnnotationsTo", """
            package addann;

            import io.micronaut.inject.annotation.ScopeOne;
            import io.micronaut.context.annotation.Bean;

            @Bean
            @io.micronaut.annotation.mapping.TransformMeToRepeatable
            class TransformToRepeatableAnnotationsTo {

                public Object myField;
            }
            """);

        // The original annotation should be transformed away
        assertFalse(definition.hasAnnotation("io.micronaut.annotation.mapping.TransformMeToRepeatable"));
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
            assertEquals(Set.of("fooT", "barT"), properties);
        } catch (ClassNotFoundException e) {
            fail(e);
        }
    }

    public static final class TheAnnotationTransformer implements TypedAnnotationTransformer<TransformMeToRepeatable> {

        @Override
        public Class<TransformMeToRepeatable> annotationType() {
            return TransformMeToRepeatable.class;
        }

        @Override
        public List<AnnotationValue<?>> transform(AnnotationValue<TransformMeToRepeatable> annotation, VisitorContext visitorContext) {
            return Arrays.asList(
                AnnotationValue.builder("io.micronaut.annotation.mapping.MyRequires").member("property", "fooT").build(),
                AnnotationValue.builder("io.micronaut.annotation.mapping.MyRequires").member("property", "barT").build()
            );
        }
    }
}
