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

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java port of AnnotateTypeArgSpec.
 */
class AnnotateTypeArgTest extends AbstractTypeElementTest {

    @Override
    protected Collection<TypeElementVisitor<?, ?>> getLocalTypeElementVisitors() {
        return List.of();
    }

    @Test
    void isAlwaysTriggeredWithoutAnnotations() {
        BeanIntrospection<?> introspection = buildBeanIntrospection("addann.AnnotateTypeArg0", """
            package addann;

            import java.util.Map;
            import io.micronaut.core.annotation.Introspected;

            @Introspected
            class AnnotateTypeArg0 {
                private Map<String, String> nameMap;
            }
            """);
        assertNotNull(introspection);
    }

    @Test
    void testAnnotating1() {
        BeanIntrospection<?> introspection = buildBeanIntrospection("addann.AnnotateTypeArg1", """
            package addann;

            import jakarta.validation.constraints.NotBlank;
            import java.util.Map;
            import io.micronaut.core.annotation.Introspected;

            @Introspected
            class AnnotateTypeArg1 {
                private Map<@NotBlank String, String> nameMap;
            }
            """);
        assertNotNull(introspection);
    }

    @Disabled("Annotation processor doesn't trigger for inner classes with type argument only annotation")
    @Test
    void testAnnotating2() {
        BeanIntrospection<?> introspection = buildBeanIntrospection("addann.Outer1$AnnotateTypeArg2", """
            package addann;

            import jakarta.validation.constraints.NotBlank;
            import java.util.Map;

            class Outer1 {
                static class AnnotateTypeArg2 {
                    private Map<@NotBlank String, String> nameMap;
                }
            }
            """);
        assertTrue(introspection.hasAnnotation(MyAnnotation.class));
    }

    @Test
    void testAnnotating3() {
        BeanIntrospection<?> introspection = buildBeanIntrospection("addann.$addann_Outer2$NotProcessedByVisitor", """
            package addann;

            import io.micronaut.core.annotation.Introspected;
            import jakarta.validation.constraints.NotBlank;
            import java.util.Map;

            @Introspected(classes = addann.Outer2.NotProcessedByVisitor.class, accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
            class Outer2 {
                static class NotProcessedByVisitor {
                    private Map<@NotBlank String, String> hnameMap;
                }
            }
            """);
        assertNotNull(introspection);
        assertEquals(List.of("hnameMap"), Arrays.asList(introspection.getPropertyNames()));
    }

    @Test
    void testAnnotating4() {
        BeanIntrospection<?> introspection = buildBeanIntrospection("addann.$addann_Outer3$NotProcessedByVisitor2", """
            package addann;

            import io.micronaut.core.annotation.Introspected;
            import jakarta.validation.constraints.NotBlank;
            import java.util.Map;

            @Introspected(classNames = "addann.Outer3.NotProcessedByVisitor2", accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
            class Outer3 {
                static class NotProcessedByVisitor2 {
                    private Map<@NotBlank String, String> hnameMap;
                }
            }
            """);
        assertNotNull(introspection);
        assertEquals(List.of("hnameMap"), Arrays.asList(introspection.getPropertyNames()));
    }

    static final class AnnotateTypeArgVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        public @NonNull VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING;
        }

        @Override
        public void visitClass(ClassElement element, VisitorContext context) {
            if (element.getSimpleName().contains("AnnotateTypeArg")) {
                element.annotate(Introspected.class);
                element.annotate(MyAnnotation.class);
            }
        }
    }
}
