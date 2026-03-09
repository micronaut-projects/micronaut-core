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
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java port of AnnotateMethodParameterSpec.
 */
class AnnotateMethodParameterTest extends AbstractTypeElementTest {

    @Override
    protected Collection<TypeElementVisitor<?, ?>> getLocalTypeElementVisitors() {
        return List.of(new AnnotateMethodParameterVisitor());
    }

    @Test
    void testAnnotating() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.AnnotateMethodParameterClass", """
            package addann;

            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Bean;

            @Bean
            class AnnotateMethodParameterClass {

                @Executable
                public MyBean1 myMethod1(MyBean1 param) {
                    return null;
                }

                @Executable
                public MyBean1 myMethod2(MyBean1 param) {
                    return null;
                }
            }

            class MyBean1 {
            }
            """);
        validate(definition);
    }

    @Test
    void testAnnotating2() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.AnnotateMethodParameterClass", """
            package addann;

            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Bean;

            @Bean
            class AnnotateMethodParameterClass<T extends MyBean1> {

                @Executable
                public T myMethod1(T param) {
                    return null;
                }

                @Executable
                public T myMethod2(T param) {
                    return null;
                }
            }

            class MyBean1 {
            }
            """);
        validate(definition);
    }

    @Test
    void testAnnotating3() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.AnnotateMethodParameterClass", """
            package addann;

            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Bean;

            @Bean
            class AnnotateMethodParameterClass<T extends MyBean1> {

                @Executable
                public <K extends T> K myMethod1(K param) {
                    return null;
                }

                @Executable
                public <K extends T> K myMethod2(K param) {
                    return null;
                }
            }

            class MyBean1 {
            }
            """);
        validate(definition);
    }

    @Test
    void testAnnotating4() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.AnnotateMethodParameterClass", """
            package addann;

            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Bean;

            abstract class Base<S> {

                @Executable
                public S myMethod1(S param) {
                    return null;
                }

                @Executable
                public S myMethod2(S param) {
                    return null;
                }
            }

            @Bean
            class AnnotateMethodParameterClass<K extends MyBean1> extends Base<K> {
            }

            class MyBean1 {
            }
            """);
        validate(definition);
    }

    @Test
    void testAnnotating6() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.AnnotateMethodParameterClass", """
            package addann;

            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Bean;

            abstract class Base<S> {

                @Executable
                public S myMethod1(S param) {
                    return null;
                }

                @Executable
                public S myMethod2(S param) {
                    return null;
                }
            }

            @Bean
            class AnnotateMethodParameterClass extends Base<MyBean1> {
            }

            class MyBean1 {
            }
            """);
        validate(definition);
    }

    private static void validate(BeanDefinition<?> definition) {
        var method1 = definition.findPossibleMethods("myMethod1").findFirst().orElseThrow();
        var method1ParameterType = method1.getArguments()[0];
        var method1ReturnType = method1.getReturnType();

        assertEquals("MyBean1", method1ParameterType.getSimpleName());
        assertEquals("MyBean1", method1ReturnType.getSimpleName());
        assertTrue(method1ParameterType.getAnnotationMetadata().hasAnnotation(MyAnnotation.class));
        assertFalse(method1ReturnType.getAnnotationMetadata().hasAnnotation(MyAnnotation.class));
        assertFalse(method1ReturnType.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation.class));
        assertFalse(method1.hasAnnotation(MyAnnotation.class));

        var method2 = definition.findPossibleMethods("myMethod2").findFirst().orElseThrow();
        var method2ParameterType = method2.getArguments()[0];
        var method2ReturnType = method2.getReturnType();

        assertFalse(method2ParameterType.getAnnotationMetadata().hasAnnotation(MyAnnotation.class));
        assertFalse(method2ReturnType.getAnnotationMetadata().hasAnnotation(MyAnnotation.class));
        assertFalse(method2ReturnType.asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation.class));
        assertFalse(method2.hasAnnotation(MyAnnotation.class));
    }

    static final class AnnotateMethodParameterVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        public void visitClass(ClassElement classElement, VisitorContext context) {
            if (!classElement.getSimpleName().equals("AnnotateMethodParameterClass")) {
                return;
            }

            MethodElement myMethod1 = classElement.findMethod("myMethod1").orElseThrow();
            ParameterElement param = myMethod1.getParameters()[0];
            ClassElement type = param.getType();
            ClassElement genericType = param.getGenericType();

            if (type instanceof GenericPlaceholderElement placeholderElement) {
                assertTrue(genericType instanceof GenericPlaceholderElement);
                GenericPlaceholderElement genericPlaceholderElement = (GenericPlaceholderElement) genericType;
                assertEquals(placeholderElement.getGenericNativeType(), genericPlaceholderElement.getGenericNativeType());
                assertEquals(placeholderElement.getVariableName(), genericPlaceholderElement.getVariableName());
            }

            // Initially no direct/type annotations on the parameter types
            assertTrue(type.getAnnotationMetadata().getAnnotationNames().isEmpty());
            assertTrue(genericType.getAnnotationMetadata().getAnnotationNames().isEmpty());

            // Annotate parameter type
            type.annotate(MyAnnotation.class);

            // Both raw and generic parameter types now expose the annotation
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(type.getAnnotationMetadata().getAnnotationNames()));
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(genericType.getAnnotationMetadata().getAnnotationNames()));

            // Annotation should be present in type-annotation metadata as well
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(type.getTypeAnnotationMetadata().getAnnotationNames()));
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(genericType.getTypeAnnotationMetadata().getAnnotationNames()));

            // The annotation is NOT added to the underlying class type
            assertTrue(type.getType().getAnnotationMetadata().isEmpty());
            assertTrue(genericType.getType().getAnnotationMetadata().isEmpty());

            // Return types remain unannotated
            assertTrue(myMethod1.getReturnType().getAnnotationMetadata().isEmpty());
            assertTrue(myMethod1.getGenericReturnType().getAnnotationMetadata().isEmpty());

            // Validate the cache is working: re-fetch from context
            ClassElement newClassElement = context.getClassElement("addann.AnnotateMethodParameterClass").orElseThrow();
            MethodElement newMethod = newClassElement.findMethod("myMethod1").orElseThrow();
            ClassElement newType = newMethod.getParameters()[0].getType();
            ClassElement newGenericType = newMethod.getParameters()[0].getGenericType();

            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(newType.getAnnotationMetadata().getAnnotationNames()));
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(newType.getTypeAnnotationMetadata().getAnnotationNames()));
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(newGenericType.getAnnotationMetadata().getAnnotationNames()));
            assertEquals(List.of(MyAnnotation.class.getName()), new ArrayList<>(newGenericType.getTypeAnnotationMetadata().getAnnotationNames()));

            assertTrue(context.getClassElement("addann.MyBean1").orElseThrow().getAnnotationMetadata().isEmpty());
            assertTrue(context.getClassElement("addann.MyBean1").orElseThrow().getTypeAnnotationMetadata().isEmpty());

            // Validate the second method's parameter remains clean
            ClassElement method2Type = newClassElement.findMethod("myMethod2").orElseThrow().getParameters()[0].getType();
            ClassElement method2GenericType = newClassElement.findMethod("myMethod2").orElseThrow().getParameters()[0].getGenericType();

            assertTrue(method2Type.getAnnotationMetadata().isEmpty());
            assertTrue(method2Type.getTypeAnnotationMetadata().isEmpty());
            assertTrue(method2GenericType.getAnnotationMetadata().isEmpty());
            assertTrue(method2GenericType.getTypeAnnotationMetadata().isEmpty());
        }
    }
}
