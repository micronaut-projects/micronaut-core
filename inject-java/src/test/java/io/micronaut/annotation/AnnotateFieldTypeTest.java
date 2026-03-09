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

import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java port of AnnotateFieldTypeSpec.
 */
class AnnotateFieldTypeTest extends AbstractTypeElementTest {

    @Override
    protected Collection<TypeElementVisitor<?, ?>> getLocalTypeElementVisitors() {
        return List.of(new AnnotateFieldTypeVisitor());
    }

    @Test
    void testAnnotating() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.AnnotateFieldTypeClass", """
            package addann;

            import io.micronaut.context.annotation.Bean;
            import jakarta.inject.Inject;

            @Bean
            class AnnotateFieldTypeClass {

                @Inject
                public MyBean1 myField1;

                @Inject
                public MyBean1 myField2;

            }

            class MyBean1 {

                private String name;

                public String getName() {
                    return name;
                }

                public void setName(String name) {
                    this.name = name;
                }

            }
            """);
        validate(definition);
    }

    @Test
    void testAnnotating2() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.AnnotateFieldTypeClass", """
            package addann;

            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Bean;
            import jakarta.inject.Inject;

            @Bean
            class AnnotateFieldTypeClass<T extends MyBean1> {

                @Inject
                public T myField1;

                @Inject
                public T myField2;
            }

            class MyBean1 {

                private String name;

                public String getName() {
                    return name;
                }

                public void setName(String name) {
                    this.name = name;
                }

            }
            """);
        validate(definition);
    }

    @Test
    void testAnnotating3() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.AnnotateFieldTypeClass", """
            package addann;

            import io.micronaut.context.annotation.Bean;
            import jakarta.inject.Inject;

            abstract class BaseAnnotateFieldTypeClass<S> {

                @Inject
                public S myField1;

                @Inject
                public S myField2;

            }

            @Bean
            class AnnotateFieldTypeClass<K extends MyBean1> extends BaseAnnotateFieldTypeClass<K> {
            }

            class MyBean1 {

                private String name;

                public String getName() {
                    return name;
                }

                public void setName(String name) {
                    this.name = name;
                }

            }
            """);
        validate(definition);
    }

    @Test
    void testAnnotating4() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.AnnotateFieldTypeClass", """
            package addann;

            import io.micronaut.context.annotation.Bean;
            import jakarta.inject.Inject;

            abstract class BaseAnnotateFieldTypeClass<S> {

                @Inject
                public S myField1;

                @Inject
                public S myField2;

            }

            @Bean
            class AnnotateFieldTypeClass extends BaseAnnotateFieldTypeClass<MyBean1> {
            }

            class MyBean1 {

                private String name;

                public String getName() {
                    return name;
                }

                public void setName(String name) {
                    this.name = name;
                }

            }
            """);
        validate(definition);
    }

    private static void validate(BeanDefinition<?> definition) {
        // Use names to avoid relying on field order
        var fields = new ArrayList<>(definition.getInjectedFields());
        var myField1 = fields.stream().filter(f -> f.getName().equals("myField1")).findFirst().orElseThrow();
        var myField2 = fields.stream().filter(f -> f.getName().equals("myField2")).findFirst().orElseThrow();

        assertEquals("myField1", myField1.getName());
        assertTrue(myField1.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation.class), "myField1 type should be annotated with @MyAnnotation");

        assertEquals("myField2", myField2.getName());
        assertFalse(myField2.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation.class), "myField2 type should NOT be annotated with @MyAnnotation");
    }

    static final class AnnotateFieldTypeVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        public void visitClass(ClassElement classElement, VisitorContext context) {
            if (!classElement.getSimpleName().equals("AnnotateFieldTypeClass")) {
                return;
            }

            FieldElement myField1 = classElement.findField("myField1").orElseThrow();
            ClassElement type = myField1.getType();
            ClassElement genericType = myField1.getGenericType();
            if (type instanceof GenericPlaceholderElement placeholderElement) {
                assertTrue(genericType instanceof GenericPlaceholderElement);
                GenericPlaceholderElement genericPlaceholderElement = (GenericPlaceholderElement) genericType;
                assertEquals(placeholderElement.getGenericNativeType(), genericPlaceholderElement.getGenericNativeType());
                assertEquals(placeholderElement.getVariableName(), genericPlaceholderElement.getVariableName());
            }

            assertTrue(type.getAnnotationMetadata().getAnnotationNames().isEmpty());
            assertTrue(genericType.getAnnotationMetadata().getAnnotationNames().isEmpty());

            type.annotate(MyAnnotation.class);

            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(type.getAnnotationMetadata().getAnnotationNames()));
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(genericType.getAnnotationMetadata().getAnnotationNames()));
            // The annotation should be added to type annotations
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(type.getTypeAnnotationMetadata().getAnnotationNames()));
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(genericType.getTypeAnnotationMetadata().getAnnotationNames()));
            // The annotation is not added to the actual underlying class type
            assertTrue(type.getType().getAnnotationMetadata().isEmpty());
            assertTrue(genericType.getType().getAnnotationMetadata().isEmpty());

            // Validate the cache is working
            ClassElement newClassElement = context.getClassElement("addann.AnnotateFieldTypeClass").orElseThrow();
            FieldElement newField = newClassElement.findField("myField1").orElseThrow();
            ClassElement newType = newField.getType();
            ClassElement newGenericType = newField.getGenericType();

            validateBeanType(newGenericType.getType());

            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(newType.getAnnotationMetadata().getAnnotationNames()));
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(newType.getTypeAnnotationMetadata().getAnnotationNames()));
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(newGenericType.getAnnotationMetadata().getAnnotationNames()));
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(newGenericType.getTypeAnnotationMetadata().getAnnotationNames()));

            // Validate the annotation is not added to the field2 type
            ClassElement field2Type = newClassElement.findField("myField2").orElseThrow().getType();
            ClassElement field2GenericType = newClassElement.findField("myField2").orElseThrow().getGenericType();

            validateBeanType(field2GenericType.getType());

            assertTrue(field2Type.getAnnotationMetadata().isEmpty());
            assertTrue(field2Type.getTypeAnnotationMetadata().isEmpty());

            assertTrue(field2GenericType.getAnnotationMetadata().isEmpty());
            assertTrue(field2GenericType.getTypeAnnotationMetadata().isEmpty());

            // Underlying types remain clean
            assertTrue(field2Type.getAnnotationMetadata().isEmpty());
            assertTrue(field2GenericType.getAnnotationMetadata().isEmpty());

            ClassElement bean = context.getClassElement("addann.MyBean1").orElseThrow();
            validateBeanType(bean);
        }

        private static void validateBeanType(ClassElement bean) {
            assertTrue(bean.getAnnotationMetadata().isEmpty());
            assertTrue(bean.getTypeAnnotationMetadata().isEmpty());
            assertEquals(2, bean.getMethods().size());
            assertEquals(1, bean.getFields().size());
        }
    }
}
