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

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.beans.BeanMethod;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.test.AbstractTypeElementTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java port of NonNullabilityAnnotationsSpec.
 */
class NonNullabilityAnnotationsTest extends AbstractTypeElementTest {

    static Stream<Arguments> nonNullPackages() {
        return Stream.of(
            Arguments.of("io.micronaut.core.annotation", "@NonNull"),
            Arguments.of("edu.umd.cs.findbugs.annotations", "@NonNull"),
            Arguments.of("javax.annotation", "@Nonnull"),
            Arguments.of("jakarta.annotation", "@Nonnull"),
            Arguments.of("org.jetbrains.annotations", "@NotNull")
        );
    }

    @ParameterizedTest
    @MethodSource("nonNullPackages")
    void testMapNonNullAnnotationInBeans(String packageName, String annotation) {
        var method = buildClassElement("""
            package test;
            import %s.*;
            @jakarta.inject.Singleton
            class Test {
                %s
                String notNullableMethod(%s String test) {
                    return "";
                }
            }
            """.formatted(packageName, annotation, annotation), element -> {
            var m = element.getEnclosedElement(
                ElementQuery.ALL_METHODS.named("notNullableMethod")
            ).orElseThrow();

            assertFalse(m.isNullable());
            assertTrue(m.isNonNull());
            assertFalse(m.getParameters()[0].isNullable());
            assertTrue(m.getParameters()[0].isNonNull());
            return m;
        });

        assertNotNull(method);
    }

    @ParameterizedTest
    @MethodSource("nonNullPackages")
    void testMapNonNullAnnotationInIntrospections(String packageName, String annotation) {
        var introspection = buildBeanIntrospection("test.Test", """
            package test;

            import %s.*;
            import io.micronaut.core.annotation.Introspected;
            import io.micronaut.context.annotation.Executable;

            @Introspected
            class Test {
                %s
                @Executable
                String notNullableMethod(%s String test) {
                    return "";
                }
            }
            """.formatted(packageName, annotation, annotation));

        BeanMethod<?, ?> beanMethod = introspection.getBeanMethods().iterator().next();

        assertFalse(beanMethod.getAnnotationMetadata().hasStereotype(AnnotationUtil.NULLABLE));
        assertTrue(beanMethod.getAnnotationMetadata().hasStereotype(AnnotationUtil.NON_NULL));
        assertFalse(beanMethod.getArguments()[0].isNullable());
        assertTrue(beanMethod.getArguments()[0].isNonNull());
    }
}
