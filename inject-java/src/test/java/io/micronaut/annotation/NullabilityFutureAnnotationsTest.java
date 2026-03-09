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

import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.test.AbstractTypeElementTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java port of NullabilityFutureAnnotationsSpec.
 */
class NullabilityFutureAnnotationsTest extends AbstractTypeElementTest {

    @Test
    void testMapNonnullOnTypeArguments() {
        var element = buildClassElement("""
            package test;
            import org.jspecify.annotations.NonNull;
            import org.jspecify.annotations.Nullable;
            import java.util.concurrent.CompletionStage;

            @jakarta.inject.Singleton
            class Test {
                CompletionStage<@NonNull String> notNullableMethod(String test) {
                    return null;
                }
                CompletionStage<@Nullable String> nullableMethod(String test) {
                    return null;
                }
                CompletionStage<String> method(String test) {
                    return null;
                }
            }
            """);

        var notNullableMethod = element.getEnclosedElement(
            ElementQuery.ALL_METHODS.named("notNullableMethod")
        ).orElseThrow();

        assertTrue(notNullableMethod.getReturnType().getFirstTypeArgument().orElseThrow().isNonNull());
        assertFalse(notNullableMethod.getReturnType().getFirstTypeArgument().orElseThrow().isNullable());

        var nullableMethod = element.getEnclosedElement(
            ElementQuery.ALL_METHODS.named("nullableMethod")
        ).orElseThrow();

        assertFalse(nullableMethod.getReturnType().getFirstTypeArgument().orElseThrow().isNonNull());
        assertTrue(nullableMethod.getReturnType().getFirstTypeArgument().orElseThrow().isNullable());

        var method = element.getEnclosedElement(
            ElementQuery.ALL_METHODS.named("method")
        ).orElseThrow();

        assertFalse(method.getReturnType().getFirstTypeArgument().orElseThrow().isNonNull());
        assertFalse(method.getReturnType().getFirstTypeArgument().orElseThrow().isNullable());
    }
}
