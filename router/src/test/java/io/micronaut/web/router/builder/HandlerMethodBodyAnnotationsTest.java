/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The body argument of a handler keeps the annotations of the given body type.
 */
class HandlerMethodBodyAnnotationsTest {

    private static final String CUSTOM = "test.EntityAnnotation";

    @Test
    void bodyArgumentKeepsAnnotationsOfTheBodyType() {
        Argument<?> body = HandlerMethod.of(annotated(), (BodyRequestHandler<List<String>>) (request, variables, b) -> HttpResponse.ok())
            .getArguments()[2];
        assertBody(body, false);
    }

    @Test
    void asyncBodyArgumentKeepsAnnotationsOfTheBodyType() {
        Argument<?> body = HandlerMethod.of(annotated(), (AsyncBodyRequestHandler<List<String>>) (request, variables, b) -> CompletableFuture.completedFuture(HttpResponse.ok()))
            .getArguments()[2];
        assertBody(body, false);
    }

    @Test
    void nullableBodyArgumentKeepsAnnotationsOfTheBodyType() {
        Argument<?> body = HandlerMethod.of(HttpRouteBuilder.nullableBody(annotated()), (BodyRequestHandler<List<String>>) (request, variables, b) -> HttpResponse.ok())
            .getArguments()[2];
        assertBody(body, true);
    }

    @Test
    void anAlreadyNullableBodyTypeStaysNullable() {
        Argument<List<String>> given = HttpRouteBuilder.nullableBody(annotated());
        Argument<?> body = HandlerMethod.of(HttpRouteBuilder.nullableBody(given), (AsyncBodyRequestHandler<List<String>>) (request, variables, b) -> CompletableFuture.completedFuture(HttpResponse.ok()))
            .getArguments()[2];
        assertBody(body, true);
    }

    @Test
    void theBodyMetadataIsTheDeclaredMetadata() {
        Argument<?> body = HandlerMethod.of(annotated(), (BodyRequestHandler<List<String>>) (request, variables, b) -> HttpResponse.ok())
            .getArguments()[2];
        AnnotationMetadata metadata = body.getAnnotationMetadata();
        assertTrue(metadata.hasDeclaredAnnotation(Body.class));
        assertFalse(metadata.hasDeclaredAnnotation(CUSTOM));
        assertTrue(metadata.hasAnnotation(CUSTOM));
    }

    @Test
    void aBodyTypeWithoutAnnotationsHasOnlyTheBodyMetadata() {
        Argument<?> body = HandlerMethod.of(Argument.of(String.class), (BodyRequestHandler<String>) (request, variables, b) -> HttpResponse.ok())
            .getArguments()[2];
        AnnotationMetadata metadata = body.getAnnotationMetadata();
        assertEquals(HandlerMethod.BODY_ARGUMENT, body.getName());
        assertTrue(metadata.hasDeclaredAnnotation(Body.class));
        assertFalse(body.isNullable());
        assertEquals(Set.of(Body.class.getName()), metadata.getAnnotationNames());
    }

    private static void assertBody(Argument<?> body, boolean nullable) {
        AnnotationMetadata metadata = body.getAnnotationMetadata();
        assertEquals(HandlerMethod.BODY_ARGUMENT, body.getName());
        assertEquals(List.class, body.getType());
        assertEquals(String.class, body.getFirstTypeVariable().orElseThrow().getType());
        assertTrue(metadata.hasAnnotation(Body.class));
        assertTrue(metadata.hasAnnotation(CUSTOM));
        assertEquals("entity", metadata.stringValue(CUSTOM).orElseThrow());
        assertEquals(3, metadata.intValue(CUSTOM, "weight").orElseThrow());
        assertEquals(nullable, body.isNullable());
        assertEquals(nullable, metadata.hasAnnotation(AnnotationUtil.NULLABLE));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Argument<List<String>> annotated() {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(CUSTOM, Map.of("value", "entity", "weight", 3));
        return (Argument) Argument.of(List.class, "entity", metadata, Argument.of(String.class));
    }
}
