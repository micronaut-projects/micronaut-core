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
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Java port of AddsUnseenRepeatableAnnotationSpec.
 */
class AddsUnseenRepeatableAnnotationTest extends AbstractTypeElementTest {

    @Override
    protected java.util.Collection<TypeElementVisitor<?, ?>> getLocalTypeElementVisitors() {
        return java.util.List.of(new AddUnseenRepeatableTypeElementVisitor());
    }

    @Test
    void testReplaceSimpleAnnotation() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.AddUnseenAnnotationsTo", """
            package addann;

            import io.micronaut.inject.annotation.ScopeOne;
            import io.micronaut.context.annotation.Bean;

            @Bean
            class AddUnseenAnnotationsTo {

                public Object myField;
            }
            """);

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
            assertEquals(Set.of("foo", "bar"), properties);
        } catch (ClassNotFoundException e) {
            fail(e);
        }
    }

    static final class AddUnseenRepeatableTypeElementVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        public void visitClass(ClassElement element, VisitorContext context) {
            if ("AddUnseenAnnotationsTo".equals(element.getSimpleName())) {
                var values = new ArrayList<AnnotationValue<?>>();
                AnnotationValueBuilder<?> b1 = AnnotationValue.builder("io.micronaut.annotation.mapping.MyRequires");
                b1.member("property", "foo");
                values.add(b1.build());
                AnnotationValueBuilder<?> b2 = AnnotationValue.builder("io.micronaut.annotation.mapping.MyRequires");
                b2.member("property", "bar");
                values.add(b2.build());
                element.annotate("io.micronaut.annotation.mapping.MyRequirements",
                    builder -> builder.values(values.toArray(new AnnotationValue<?>[0])));
            }
        }
    }
}
