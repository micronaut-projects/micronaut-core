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

import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Executable;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java port of AnnotateMethodSpec.
 */
class AnnotateMethodTest extends AbstractTypeElementTest {

    @Override
    protected Collection<TypeElementVisitor<?, ?>> getLocalTypeElementVisitors() {
        return List.of(new AnnotationMethodVisitor());
    }

    @Test
    void testAnnotating() {
        BeanDefinition<?> definition = buildBeanDefinition("addann.AnnotationMethodClass", """
            package addann;

            import io.micronaut.context.annotation.Executable;
            import io.micronaut.context.annotation.Bean;

            @Bean
            class AnnotationMethodClass {

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
            }
            """);

        // myMethod1 has added annotation on the method and it's seen on the return type
        assertTrue(definition.getRequiredMethod("myMethod1").hasAnnotation(MyAnnotation.class));
        assertTrue(definition.getRequiredMethod("myMethod1").getReturnType().getAnnotationMetadata().hasAnnotation(MyAnnotation.class));
        assertTrue(definition.getRequiredMethod("myMethod1").getReturnType().asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation.class));

        // myMethod2 doesn't have the same annotation on the same type
        assertFalse(definition.getRequiredMethod("myMethod2").hasAnnotation(MyAnnotation.class));
        assertFalse(definition.getRequiredMethod("myMethod2").getReturnType().getAnnotationMetadata().hasAnnotation(MyAnnotation.class));
        assertFalse(definition.getRequiredMethod("myMethod2").getReturnType().asArgument().getAnnotationMetadata().hasAnnotation(MyAnnotation.class));
    }

    static final class AnnotationMethodVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        public void visitClass(ClassElement element, VisitorContext context) {
            if (element.getSimpleName().equals("AnnotationMethodClass")) {
                MethodElement myMethod1 = element.findMethod("myMethod1").orElseThrow();
                // Expect initial annotations present on the method
                assertEquals(
                    List.of(Executable.class.getName(), Bean.class.getName()),
                    new ArrayList<>(myMethod1.getAnnotationMetadata().getAnnotationNames())
                );
                myMethod1.annotate(MyAnnotation.class);
                assertEquals(
                    List.of(Executable.class.getName(), Bean.class.getName(), MyAnnotation.class.getName()),
                    new ArrayList<>(myMethod1.getAnnotationMetadata().getAnnotationNames())
                );

                // Return type should remain without direct/type annotations (only method gets annotated)
                assertTrue(myMethod1.getReturnType().getAnnotationNames().isEmpty());
                assertTrue(myMethod1.getReturnType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty());
                assertTrue(myMethod1.getReturnType().getType().getAnnotationMetadata().isEmpty());

                // Validate the cache is working
                assertEquals(
                    List.of(Executable.class.getName(), Bean.class.getName(), MyAnnotation.class.getName()),
                    new ArrayList<>(context.getClassElement("addann.AnnotationMethodClass").orElseThrow()
                        .findMethod("myMethod1").orElseThrow()
                        .getAnnotationMetadata().getAnnotationNames())
                );

                // Test the second method with the same type doesn't have the annotations
                MethodElement myMethod2 = element.findMethod("myMethod2").orElseThrow();
                assertEquals(
                    List.of(Executable.class.getName(), Bean.class.getName()),
                    new ArrayList<>(myMethod2.getAnnotationMetadata().getAnnotationNames())
                );
                assertTrue(myMethod2.getReturnType().getAnnotationNames().isEmpty());
                assertTrue(myMethod2.getReturnType().getTypeAnnotationMetadata().getAnnotationNames().isEmpty());

                // Validate the cache is working
                assertEquals(
                    List.of(Executable.class.getName(), Bean.class.getName()),
                    new ArrayList<>(context.getClassElement("addann.AnnotationMethodClass").orElseThrow()
                        .findMethod("myMethod2").orElseThrow()
                        .getAnnotationMetadata().getAnnotationNames())
                );

                // The referenced type should not receive annotations
                assertTrue(context.getClassElement("addann.MyBean1").orElseThrow().getAnnotationMetadata().isEmpty());
            }
        }
    }
}
