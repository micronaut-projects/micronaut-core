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
package io.micronaut.annotation;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java port of AnnotatePropertySpec.
 */
class AnnotatePropertyTest extends AbstractTypeElementTest {

    @Override
    protected Collection<TypeElementVisitor<?, ?>> getLocalTypeElementVisitors() {
        return List.of(new AnnotatePropertyVisitor());
    }

    @Test
    void testAnnotating1() {
        buildClassElement("""
            package annotateprop;

            import io.micronaut.core.annotation.Introspected;

            @Introspected
            class AnnotatePropertyBean1 {

                private String firstName;
                private String lastName;

                public void setFirstName(String firstName) {
                    this.firstName = firstName;
                }

                public String getFirstName() {
                    return firstName;
                }

                public void setLastName(String lastName) {
                    this.lastName = lastName;
                }

                public String getLastName() {
                    return lastName;
                }
            }
            """);
    }

    @Test
    void testAnnotating2() {
        buildClassElement("""
            package annotateprop;

            import io.micronaut.core.annotation.Introspected;

            @Introspected
            class AnnotatePropertyBean2 {

                private final String firstName;
                private final String lastName;

                public AnnotatePropertyBean2(String firstName, String lastName) {
                    this.firstName = firstName;
                    this.lastName = lastName;
                }

                public String getFirstName() {
                    return firstName;
                }

                public String getLastName() {
                    return lastName;
                }
            }
            """);
    }

    private static void validate(BeanIntrospection<?> introspection) {
        var property1 = introspection.getRequiredProperty("firstName", String.class);
        assertEquals("firstName", property1.getName());
        assertTrue(property1.hasAnnotation(MyAnnotation.class));
        assertTrue(property1.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation.class));

        var property2 = introspection.getRequiredProperty("lastName", String.class);
        assertEquals("lastName", property2.getName());
        assertFalse(property2.hasAnnotation(MyAnnotation.class));
        assertFalse(property2.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation.class));
    }

    static final class AnnotatePropertyVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        public @NonNull VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING;
        }

        @Override
        public void visitClass(ClassElement classElement, VisitorContext context) {
            if (!classElement.getSimpleName().startsWith("AnnotatePropertyBean")) {
                return;
            }

            List<? extends PropertyElement> properties = classElement.getBeanProperties();
            assertEquals(2, properties.size());

            PropertyElement property1 = properties.get(0);
            assertEquals("firstName", property1.getName());

            assertTrue(property1.getAnnotationMetadata().getAnnotationNames().isEmpty());
            assertTrue(property1.getType().getAnnotationMetadata().getAnnotationNames().isEmpty());
            assertTrue(property1.getGenericType().getAnnotationMetadata().getAnnotationNames().isEmpty());

            property1.annotate(MyAnnotation.class);

            assertTrue(property1.hasAnnotation(MyAnnotation.class));
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(property1.getAnnotationMetadata().getAnnotationNames()));
            assertTrue(property1.getType().getAnnotationMetadata().getAnnotationNames().isEmpty());
            assertTrue(property1.getGenericType().getAnnotationMetadata().getAnnotationNames().isEmpty());

            FieldElement field1 = property1.getField().orElse(null);
            if (field1 != null) {
                assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(field1.getAnnotationMetadata().getAnnotationNames()));
                assertTrue(field1.getType().getAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(field1.getGenericType().getAnnotationMetadata().getAnnotationNames().isEmpty());
            }

            MethodElement getter1 = (MethodElement) property1.getReadMember().orElse(null);
            if (getter1 != null) {
                assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(getter1.getMethodAnnotationMetadata().getAnnotationNames()));
                assertTrue(getter1.hasAnnotation(MyAnnotation.class));
                assertTrue(getter1.getReturnType().getAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(getter1.getGenericReturnType().getAnnotationMetadata().getAnnotationNames().isEmpty());
            }

            MethodElement setter1 = (MethodElement) property1.getWriteMember().orElse(null);
            if (setter1 != null) {
                assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(setter1.getMethodAnnotationMetadata().getAnnotationNames()));
                assertTrue(setter1.hasAnnotation(MyAnnotation.class));
                assertTrue(setter1.getReturnType().getAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(setter1.getGenericReturnType().getAnnotationMetadata().getAnnotationNames().isEmpty());

                ParameterElement parameter = setter1.getParameters()[0];
                // TODO: delegate to the parameter
                // assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(parameter.getAnnotationNames()));
                assertTrue(parameter.getAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(parameter.getType().getAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(parameter.getGenericType().getAnnotationMetadata().getAnnotationNames().isEmpty());
            }

            PropertyElement property2 = properties.get(1);
            assertEquals("lastName", property2.getName());
            assertTrue(property2.getAnnotationMetadata().getAnnotationNames().isEmpty());
            assertTrue(property2.getType().getAnnotationMetadata().getAnnotationNames().isEmpty());
            assertTrue(property2.getGenericType().getAnnotationMetadata().getAnnotationNames().isEmpty());

            FieldElement field2 = property2.getField().orElse(null);
            if (field2 != null) {
                assertTrue(field2.getAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(field2.getType().getAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(field2.getGenericType().getAnnotationMetadata().getAnnotationNames().isEmpty());
            }

            MethodElement getter2 = (MethodElement) property2.getReadMember().orElse(null);
            if (getter2 != null) {
                assertTrue(getter2.getMethodAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(getter2.getReturnType().getAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(getter2.getGenericReturnType().getAnnotationMetadata().getAnnotationNames().isEmpty());
            }

            MethodElement setter2 = (MethodElement) property2.getWriteMember().orElse(null);
            if (setter2 != null) {
                assertTrue(setter2.getMethodAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(setter2.getReturnType().getAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(setter2.getGenericReturnType().getAnnotationMetadata().getAnnotationNames().isEmpty());

                ParameterElement parameter = setter2.getParameters()[0];
                assertTrue(parameter.getAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(parameter.getType().getAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(parameter.getGenericType().getAnnotationMetadata().getAnnotationNames().isEmpty());
            }

            // Validate the cache is working
            ClassElement newClassElement = context.getClassElement(classElement.getName()).orElseThrow();
            PropertyElement newProperty = newClassElement.getBeanProperties().get(0);
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(newProperty.getAnnotationMetadata().getAnnotationNames()));
            assertTrue(newProperty.getType().getAnnotationMetadata().getAnnotationNames().isEmpty());
            assertTrue(newProperty.getGenericType().getAnnotationMetadata().getAnnotationNames().isEmpty());

            FieldElement field1new = newProperty.getField().orElse(null);
            if (field1new != null) {
                assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(field1new.getAnnotationMetadata().getAnnotationNames()));
                assertTrue(field1new.getType().getAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(field1new.getGenericType().getAnnotationMetadata().getAnnotationNames().isEmpty());
            }

            MethodElement getter1new = (MethodElement) newProperty.getReadMember().orElse(null);
            if (getter1new != null) {
                assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(getter1new.getMethodAnnotationMetadata().getAnnotationNames()));
                assertTrue(getter1new.hasAnnotation(MyAnnotation.class));
                assertTrue(getter1new.getReturnType().getAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(getter1new.getGenericReturnType().getAnnotationMetadata().getAnnotationNames().isEmpty());
            }

            MethodElement setter1new = (MethodElement) newProperty.getWriteMember().orElse(null);
            if (setter1new != null) {
                assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(setter1new.getMethodAnnotationMetadata().getAnnotationNames()));
                assertTrue(setter1new.hasAnnotation(MyAnnotation.class));
                assertTrue(setter1new.getReturnType().getAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(setter1new.getGenericReturnType().getAnnotationMetadata().getAnnotationNames().isEmpty());

                ParameterElement parameter = setter1new.getParameters()[0];
                // TODO: delegate to the parameter
                // assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(parameter.getAnnotationNames()));
                assertTrue(parameter.getAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(parameter.getType().getAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(parameter.getGenericType().getAnnotationMetadata().getAnnotationNames().isEmpty());
            }
        }
    }
}
