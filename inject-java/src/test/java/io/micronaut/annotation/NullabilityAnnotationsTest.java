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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java port of NullabilityAnnotationsSpec.
 */
class NullabilityAnnotationsTest extends AbstractTypeElementTest {

    static List<String> packages() {
        return List.of(
            "io.micronaut.core.annotation",
            "javax.annotation",
            "org.jetbrains.annotations",
            "jakarta.annotation",
            "edu.umd.cs.findbugs.annotations"
        );
    }

    @ParameterizedTest
    @MethodSource("packages")
    void testMapNullableAnnotationInBeans(String packageName) {
        var element = buildClassElement("""
            package test;
            import %s.*;
            @jakarta.inject.Singleton
            class Test {
                @Nullable
                String nullableMethod(@Nullable String test) {
                    return null;
                }
            }
            """.formatted(packageName));

        var nullableMethod = element.getEnclosedElement(
            ElementQuery.ALL_METHODS.named("nullableMethod")
        ).orElseThrow();

        assertTrue(nullableMethod.isNullable());
        assertFalse(nullableMethod.isNonNull());
        assertTrue(nullableMethod.getParameters()[0].isNullable());
        assertFalse(nullableMethod.getParameters()[0].isNonNull());
    }

    @ParameterizedTest
    @MethodSource("packages")
    void testMapNullableAnnotationInIntrospections(String packageName) {
        var introspection = buildBeanIntrospection("test.Test", """
            package test;

            import %s.*;
            import io.micronaut.core.annotation.Introspected;
            import io.micronaut.context.annotation.Executable;

            @Introspected
            class Test {
                @Nullable
                @Executable
                String nullableMethod(@Nullable String test) {
                    return null;
                }
            }
            """.formatted(packageName));

        BeanMethod<?, ?> nullableMethod = introspection.getBeanMethods().iterator().next();

        assertTrue(nullableMethod.getAnnotationMetadata().hasStereotype(AnnotationUtil.NULLABLE));
        assertFalse(nullableMethod.getAnnotationMetadata().hasStereotype(AnnotationUtil.NON_NULL));
        assertTrue(nullableMethod.getArguments()[0].isNullable());
        assertFalse(nullableMethod.getArguments()[0].isNonNull());
    }
}
