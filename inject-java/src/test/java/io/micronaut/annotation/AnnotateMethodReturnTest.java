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
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java port of AnnotateMethodReturnSpec.
 */
class AnnotateMethodReturnTest extends AbstractTypeElementTest {

    @Override
    protected Collection<TypeElementVisitor<?, ?>> getLocalTypeElementVisitors() {
        return List.of(new AnnotateMethodReturnVisitor());
    }

    @Test
    void testAnnotating() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.AnnotateMethodReturnClass", """
            package addann;

            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Bean;

            @Bean
            class AnnotateMethodReturnClass {

                @Executable
                public MyBean1 myMethod1() {
                    return null;
                }

                @Executable
                public MyBean1 myMethod2() {
                    return null;
                }
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
        BeanDefinition<?> definition = buildBeanDefinition("addann.AnnotateMethodReturnClass", """
            package addann;

            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Bean;

            @Bean
            class AnnotateMethodReturnClass<T extends MyBean1> {

                @Executable
                public T myMethod1() {
                    return null;
                }

                @Executable
                public T myMethod2() {
                    return null;
                }
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
        BeanDefinition<?> definition = buildBeanDefinition("addann.AnnotateMethodReturnClass", """
            package addann;

            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Bean;

            @Bean
            class AnnotateMethodReturnClass<T extends MyBean1> {

                @Executable
                public <K extends T> K myMethod1() {
                    return null;
                }

                @Executable
                public <K extends T> K myMethod2() {
                    return null;
                }
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
        BeanDefinition<?> definition = buildBeanDefinition("addann.AnnotateMethodReturnClass", """
            package addann;

            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Bean;

            abstract class BaseAnnotateMethodReturnClass<S> {

                @Executable
                public S myMethod1() {
                    return null;
                }

                @Executable
                public S myMethod2() {
                    return null;
                }
            }

            @Bean
            class AnnotateMethodReturnClass<K extends MyBean1> extends BaseAnnotateMethodReturnClass<K> {
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
    void testAnnotating6() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.AnnotateMethodReturnClass", """
            package addann;

            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Bean;

            abstract class BaseAnnotateMethodReturnClass<S> {

                @Executable
                public S myMethod1() {
                    return null;
                }

                @Executable
                public S myMethod2() {
                    return null;
                }
            }

            @Bean
            class AnnotateMethodReturnClass extends BaseAnnotateMethodReturnClass<MyBean1> {
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
        var method1 = definition.getRequiredMethod("myMethod1");
        var method1ReturnType = method1.getReturnType();

        assertEquals("MyBean1", method1ReturnType.getSimpleName());
        assertTrue(method1ReturnType.getAnnotationMetadata().hasAnnotation(MyAnnotation.class));
        assertTrue(method1ReturnType.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation.class));
        assertFalse(method1.hasAnnotation(MyAnnotation.class));

        var method2 = definition.getRequiredMethod("myMethod2");
        var method2ReturnType = method2.getReturnType();

        assertFalse(method2ReturnType.getAnnotationMetadata().hasAnnotation(MyAnnotation.class));
        assertFalse(method2ReturnType.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation.class));
        assertFalse(method2.hasAnnotation(MyAnnotation.class));
    }

    static final class AnnotateMethodReturnVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        public void visitClass(ClassElement classElement, VisitorContext context) {
            if (!classElement.getSimpleName().equals("AnnotateMethodReturnClass")) {
                return;
            }

            MethodElement myMethod1 = classElement.findMethod("myMethod1").orElseThrow();
            ClassElement returnType = myMethod1.getReturnType();
            ClassElement genericReturnType = myMethod1.getGenericReturnType();

            if (returnType instanceof GenericPlaceholderElement placeholderElement) {
                assertTrue(genericReturnType instanceof GenericPlaceholderElement);
                GenericPlaceholderElement genericPlaceholderElement = (GenericPlaceholderElement) genericReturnType;

                assertEquals(placeholderElement.getGenericNativeType(), genericPlaceholderElement.getGenericNativeType());
                assertEquals(placeholderElement.getVariableName(), genericPlaceholderElement.getVariableName());
                // Note: The Groovy spec contained commented asserts about declaringElement; they are not asserted here.
            }

            // Initially no direct/type annotations on the return types
            assertTrue(returnType.getAnnotationMetadata().getAnnotationNames().isEmpty());
            assertTrue(genericReturnType.getAnnotationMetadata().getAnnotationNames().isEmpty());

            // Annotate return type
            returnType.annotate(MyAnnotation.class);

            // Both raw and generic return types now expose the annotation
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(returnType.getAnnotationMetadata().getAnnotationNames()));
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(genericReturnType.getAnnotationMetadata().getAnnotationNames()));

            // Annotation should be present in type-annotation metadata as well
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(returnType.getTypeAnnotationMetadata().getAnnotationNames()));
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(genericReturnType.getTypeAnnotationMetadata().getAnnotationNames()));

            // The annotation is NOT added to the underlying class type
            assertTrue(returnType.getType().getAnnotationMetadata().isEmpty());
            assertTrue(genericReturnType.getType().getAnnotationMetadata().isEmpty());

            // Validate the cache is working: re-fetch from context
            ClassElement newClassElement = context.getClassElement("addann.AnnotateMethodReturnClass").orElseThrow();
            MethodElement newMethod = newClassElement.findMethod("myMethod1").orElseThrow();
            ClassElement newReturnType = newMethod.getReturnType();
            ClassElement newGenericReturnType = newMethod.getGenericReturnType();

            validateBeanType(newGenericReturnType.getType());

            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(newReturnType.getAnnotationMetadata().getAnnotationNames()));
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(newReturnType.getTypeAnnotationMetadata().getAnnotationNames()));
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(newGenericReturnType.getAnnotationMetadata().getAnnotationNames()));
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(newGenericReturnType.getTypeAnnotationMetadata().getAnnotationNames()));

            // Ensure the other method's return type remains unaffected
            ClassElement method2ReturnType = newClassElement.findMethod("myMethod2").orElseThrow().getReturnType();
            ClassElement method2GenericReturnType = newClassElement.findMethod("myMethod2").orElseThrow().getGenericReturnType();

            validateBeanType(method2GenericReturnType.getType());

            assertTrue(method2ReturnType.getAnnotationMetadata().isEmpty());
            assertTrue(method2ReturnType.getTypeAnnotationMetadata().isEmpty());
            assertTrue(method2GenericReturnType.getAnnotationMetadata().isEmpty());
            assertTrue(method2GenericReturnType.getTypeAnnotationMetadata().isEmpty());

            // Underlying types should remain clean
            assertTrue(method2ReturnType.getAnnotationMetadata().isEmpty());
            assertTrue(method2GenericReturnType.getAnnotationMetadata().isEmpty());

            // The referenced bean type itself should not receive annotations
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
