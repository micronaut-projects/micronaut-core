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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java port of AddsRepeatableAnnotationSpec.
 */
class AddsRepeatableAnnotationTest extends AbstractTypeElementTest {

    @Override
    protected java.util.Collection<TypeElementVisitor<?, ?>> getLocalTypeElementVisitors() {
        return java.util.List.of(new AddRepeatableTypeElementVisitor());
    }

    @Test
    void testReplaceSimpleAnnotation() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.AddAnnotationsTo", """
            package addann;

            import io.micronaut.inject.annotation.ScopeOne;
            import io.micronaut.context.annotation.Bean;

            @io.micronaut.annotation.mapping.MyRequires(property = "foo")
            @io.micronaut.annotation.mapping.MyRequires(property = "bar")
            @Bean
            class AddAnnotationsTo {

                @io.micronaut.annotation.mapping.MyRequires(property = "xyz")
                public Object myField;
            }
            """);

        try {
            @SuppressWarnings("unchecked")
            Class<? extends java.lang.annotation.Annotation> myRequires =
                (Class<? extends java.lang.annotation.Annotation>) definition.getClass().getClassLoader()
                    .loadClass("io.micronaut.annotation.mapping.MyRequires");
            var values = definition.getAnnotationMetadata().getAnnotationValuesByType(myRequires);
            assertEquals(3, values.size());
            Set<String> properties = values.stream()
                .flatMap(av -> av.stringValue("property").stream())
                .collect(Collectors.toSet());
            assertEquals(Set.of("foo", "bar", "xyz"), properties);
        } catch (ClassNotFoundException e) {
            fail(e);
        }
    }

    static final class AddRepeatableTypeElementVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        public void visitClass(ClassElement element, VisitorContext context) {
            if ("AddAnnotationsTo".equals(element.getSimpleName())) {
                element.annotate("io.micronaut.annotation.mapping.MyRequirements", builder -> builder.values(
                    element.getFields().stream().flatMap(this::getIndexes).toArray(AnnotationValue[]::new)
                ));
            }
        }

        private Stream<AnnotationValue<?>> getIndexes(AnnotationMetadata am) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends java.lang.annotation.Annotation> myRequires =
                    (Class<? extends java.lang.annotation.Annotation>) AddsRepeatableAnnotationTest.class.getClassLoader()
                        .loadClass("io.micronaut.annotation.mapping.MyRequires");
                var values = am.getAnnotationValuesByType(myRequires);
                if (values.isEmpty()) {
                    throw new IllegalStateException();
                }
                return values.stream().map(av -> {
                    AnnotationValueBuilder<?> b = AnnotationValue.builder("io.micronaut.annotation.mapping.MyRequires");
                    av.stringValue("property").ifPresent(p -> b.member("property", p));
                    return b.build();
                });
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
