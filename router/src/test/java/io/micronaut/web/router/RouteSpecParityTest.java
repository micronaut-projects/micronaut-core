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
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.RequestPredicates;
import io.micronaut.web.router.builder.RouteSpec;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every setting of {@link RouteSpec} is available on a route and on a group, and a route of a
 * group without its own settings has the settings of the group, as if they were given to it.
 */
class RouteSpecParityTest {

    private static final AnnotationMetadataProvider ELEMENT = element();

    @Test
    void everySettingOfARouteIsASettingOfAGroupItsRoutesInherit() {
        Router router = router(routes -> {
            configure(routes.GET("/route", (request, pathVariables) -> HttpResponse.ok()));
            routes.group(group -> {
                group.GET("/grouped", (request, pathVariables) -> HttpResponse.ok());
                configure(group);
            });
        });
        for (String path : List.of("/route", "/grouped")) {
            // the condition of the settings
            assertNull(router.findClosest(RoutePortTest.onPort(HttpRequest.GET(path).contentType(MediaType.TEXT_PLAIN_TYPE), 9091)), path);
            // the port of the settings
            assertNull(router.findClosest(RoutePortTest.onPort(HttpRequest.GET(path).contentType(MediaType.TEXT_PLAIN_TYPE)
                .header("X-Parity", "yes"), 9090)), path);
            UriRouteMatch<Object, Object> match = router.findClosest(RoutePortTest.onPort(HttpRequest.GET(path)
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header("X-Parity", "yes"), 9091));
            assertNotNull(match, path);
            UriRouteInfo<Object, Object> route = match.getRouteInfo();
            assertEquals(List.of(MediaType.TEXT_PLAIN_TYPE), route.getConsumes(), path);
            assertEquals(List.of(MediaType.TEXT_HTML_TYPE), route.getProduces(), path);
            assertEquals(7, route.getOrder(), path);
            assertEquals(9091, route.getPort(), path);
            assertEquals("value", route.getAttribute("name", String.class).orElseThrow(), path);
            AnnotationMetadata annotations = route.getAnnotationMetadata();
            assertTrue(annotations.hasAnnotation(Marker.class), path);
            assertTrue(annotations.hasAnnotation(Other.class), path);
            assertTrue(annotations.hasAnnotation(ByName.class), path);
            assertEquals("2", annotations.stringValue(Version.class).orElseThrow(), path);
            assertEquals("b", annotations.stringValue(Tagged.class).orElseThrow(), path);
            // the element, whose annotations the given ones override
            assertTrue(annotations.hasAnnotation(FromElement.class), path);
            assertSame(ELEMENT, ((MethodBasedRouteInfo<?, ?>) route).getAnnotationMetadataProvider().orElseThrow(), path);
        }
    }

    /**
     * Call every setting, through the type both routes and groups have.
     */
    private static void configure(RouteSpec<?> spec) {
        spec.consumesAll();
        spec.consumes(MediaType.TEXT_PLAIN_TYPE);
        spec.produces(MediaType.TEXT_HTML_TYPE);
        spec.nonBlocking();
        spec.executeOn(TaskExecutors.BLOCKING);
        spec.annotationMetadata(ELEMENT);
        spec.annotate(AnnotationValue.builder(Version.class).value("1").build());
        spec.annotate(Version.class, version -> version.value("2"));
        spec.annotate(Marker.class);
        spec.annotate(Other.class.getName());
        spec.annotate(ByName.class.getName(), builder -> { });
        spec.annotate(Tagged.class.getName(), tagged -> tagged.value("a"));
        spec.annotate(Tagged.class, tagged -> tagged.value("b"));
        spec.attribute("name", "value");
        spec.where(RequestPredicates.header("X-Parity"));
        spec.order(7);
        spec.port(9090);
        spec.port("9091");
    }

    private static AnnotationMetadataProvider element() {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(FromElement.class.getName(), Map.of());
        metadata.addDeclaredAnnotation(Version.class.getName(), Map.of("value", "0"));
        return new AnnotationMetadataProvider() {
            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                return metadata;
            }
        };
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        routes.accept(builder);
        builder.close();
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Marker {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Other {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface ByName {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Tagged {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface FromElement {
    }
}
