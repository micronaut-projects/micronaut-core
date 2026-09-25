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
package io.micronaut.web.router;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The body argument of a handler keeps the annotations of the body type it was given, e.g. a
 * {@code @JsonView} the decoder reads, and adds {@code @Body}, like a {@code @Body} parameter of a
 * controller that has both.
 */
class HandlerRouteBodyAnnotationsTest {

    @Test
    void theBodyArgumentKeepsTheAnnotationsOfTheBodyType() {
        Argument<Item> bodyType = Argument.of(Item.class, "item", view("public"));
        Router router = router(routes -> routes.POST("/items", bodyType, (request, pathVariables, item) -> HttpResponse.ok()));

        Argument<?> body = bodyOf(router, HttpRequest.POST("/items", ""));

        assertEquals(Item.class, body.getType());
        assertTrue(body.getAnnotationMetadata().hasAnnotation(Body.class));
        assertEquals("public", body.getAnnotationMetadata().stringValue(View.class).orElse(null));
        assertTrue(body.getAnnotationMetadata().hasDeclaredAnnotation(View.class));
    }

    @Test
    void aNullableBodyArgumentKeepsTheAnnotationsOfTheBodyType() {
        Argument<Item> bodyType = HttpRouteBuilder.nullableBody(Argument.of(Item.class, "item", view("public")));
        Router router = router(routes -> routes.POST("/items", bodyType, (request, pathVariables, item) -> HttpResponse.ok()));

        Argument<?> body = bodyOf(router, HttpRequest.POST("/items", ""));

        assertTrue(body.getAnnotationMetadata().hasAnnotation(Body.class));
        assertTrue(body.isNullable());
        assertEquals("public", body.getAnnotationMetadata().stringValue(View.class).orElse(null));
    }

    @Test
    void theBodyArgumentOfABodyTypeReadByAHandlerKeepsItsAnnotations() {
        // the argument an asynchronous handler's body(Argument) is bound with
        Argument<Item> body = HandlerMethod.bodyArgument(Argument.of(Item.class, "item", view("public")));

        assertTrue(body.getAnnotationMetadata().hasAnnotation(Body.class));
        assertEquals("public", body.getAnnotationMetadata().stringValue(View.class).orElse(null));
    }

    private static AnnotationMetadata view(String value) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(View.class.getName(), Map.of(AnnotationMetadata.VALUE_MEMBER, value));
        return metadata;
    }

    private static Argument<?> bodyOf(Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getMethodName() + " " + request.getPath());
        return match.getRouteInfo().getRequestBodyType().orElseThrow();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        routes.accept(builder);
        builder.close();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface View {
        String value();
    }

    record Item(String name) {
    }
}
