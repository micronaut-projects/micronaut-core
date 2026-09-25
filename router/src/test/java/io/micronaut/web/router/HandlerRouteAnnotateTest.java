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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.RouteDeclaration;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The annotations put on handler routes and groups with {@code annotate(...)}, layered over the
 * annotations of the element given with {@code annotationMetadata(...)}.
 */
class HandlerRouteAnnotateTest {

    private static final String CUSTOM = "io.micronaut.web.router.HandlerRouteAnnotateTest.Custom";

    @Test
    void aMarkerAnnotationIsDeclaredOnTheRoute() {
        Router router = router(routes -> {
            routes.GET("/marked", (request, pathVariables) -> HttpResponse.ok()).annotate(Marker.class);
            routes.GET("/plain", (request, pathVariables) -> HttpResponse.ok());
        });
        AnnotationMetadata marked = route(router, "/marked").getAnnotationMetadata();
        assertTrue(marked.hasAnnotation(Marker.class));
        assertTrue(marked.hasDeclaredAnnotation(Marker.class));
        // what the binding of a @FilterMatcher filter checks
        assertTrue(marked.hasStereotype(Marker.class.getName()));
        assertTrue(route(router, "/marked").getReturnType().getAnnotationMetadata().hasAnnotation(Marker.class));
        assertTrue(route(router, "/plain").getAnnotationMetadata().isEmpty());
    }

    @Test
    void aLaterAnnotationOfTheSameTypeReplacesAnEarlierOne() {
        Router router = router(routes -> routes.GET("/ping", (request, pathVariables) -> HttpResponse.ok())
            .annotate(AnnotationValue.builder(Version.class).value("1").build())
            .annotate(AnnotationValue.builder(Version.class).value("2").build()));
        assertEquals("2", route(router, "/ping").getAnnotationMetadata().stringValue(Version.class).orElseThrow());
    }

    @Test
    void theMembersOfAnAnnotationGivenAgainAreMergedLikeOnAnElement() {
        Router router = router(routes -> {
            routes.GET("/merged", (request, pathVariables) -> HttpResponse.ok())
                .annotate(CUSTOM, custom -> custom.member("first", 1).member("second", 1))
                .annotate(CUSTOM, custom -> custom.member("second", 2));
            routes.group(group -> {
                group.annotate(CUSTOM, custom -> custom.member("first", 1).member("second", 1));
                group.GET("/grouped-merged", (request, pathVariables) -> HttpResponse.ok())
                    .annotate(CUSTOM, custom -> custom.member("second", 2));
            });
        });
        for (String path : List.of("/merged", "/grouped-merged")) {
            AnnotationMetadata metadata = route(router, path).getAnnotationMetadata();
            assertEquals(1, metadata.intValue(CUSTOM, "first").orElseThrow(), path);
            assertEquals(2, metadata.intValue(CUSTOM, "second").orElseThrow(), path);
        }
    }

    @Test
    void theRoutesOfAGroupHaveItsAnnotationsWhichNestedGroupsAndRoutesOverride() {
        Router router = router(routes -> routes.path("/g", group -> {
            group.GET("/declared-before", (request, pathVariables) -> HttpResponse.ok());
            group.annotate(Marker.class);
            group.annotate(AnnotationValue.builder(Version.class).value("1").build());
            group.GET("/group", (request, pathVariables) -> HttpResponse.ok());
            group.GET("/route", (request, pathVariables) -> HttpResponse.ok())
                .annotate(AnnotationValue.builder(Version.class).value("3").build());
            group.path("/nested", nested -> {
                nested.annotate(AnnotationValue.builder(Version.class).value("2").build());
                nested.GET("/ping", (request, pathVariables) -> HttpResponse.ok());
            });
        }));
        for (String path : List.of("/g/declared-before", "/g/group")) {
            AnnotationMetadata metadata = route(router, path).getAnnotationMetadata();
            assertTrue(metadata.hasAnnotation(Marker.class), path);
            assertEquals("1", metadata.stringValue(Version.class).orElseThrow(), path);
        }
        assertEquals("3", route(router, "/g/route").getAnnotationMetadata().stringValue(Version.class).orElseThrow());
        AnnotationMetadata nested = route(router, "/g/nested/ping").getAnnotationMetadata();
        assertEquals("2", nested.stringValue(Version.class).orElseThrow());
        assertTrue(nested.hasAnnotation(Marker.class));
    }

    @Test
    void severalChainedAnnotationsIncludingAStereotype() {
        AnnotationValue<?> custom = AnnotationValue.builder(CUSTOM)
            .stereotype(AnnotationValue.builder(FilterMatcher.class).build())
            .build();
        Router router = router(routes -> {
            routes.GET("/several", (request, pathVariables) -> HttpResponse.ok())
                .annotate(Marker.class)
                .annotate(Version.class, version -> version.value("1"))
                .annotate(custom)
                .annotate(Version.class.getName(), version -> version.value("2"));
            routes.group(group -> {
                group.annotate(Marker.class.getName()).annotate(custom);
                group.GET("/grouped", (request, pathVariables) -> HttpResponse.ok());
            });
        });
        for (String path : List.of("/several", "/grouped")) {
            AnnotationMetadata metadata = route(router, path).getAnnotationMetadata();
            assertTrue(metadata.hasAnnotation(Marker.class), path);
            assertTrue(metadata.hasStereotype(FilterMatcher.class), path);
            assertEquals(CUSTOM, metadata.getAnnotationNameByStereotype(FilterMatcher.class).orElseThrow(), path);
        }
        assertEquals("2", route(router, "/several").getAnnotationMetadata().stringValue(Version.class).orElseThrow());
    }

    @Test
    void theAnnotationsOfTheRouteOverrideTheOnesOfItsElement() {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(Version.class.getName(), Map.of("value", "1"));
        metadata.addDeclaredAnnotation(Other.class.getName(), Map.of());
        AnnotationMetadataProvider element = new AnnotationMetadataProvider() {
            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                return metadata;
            }
        };
        Router router = router(routes -> {
            routes.GET("/element", (request, pathVariables) -> HttpResponse.ok())
                .annotate(Marker.class)
                .annotationMetadata(element);
            routes.GET("/override", (request, pathVariables) -> HttpResponse.ok())
                .annotationMetadata(element)
                .annotate(AnnotationValue.builder(Version.class).value("2").build());
        });
        MethodBasedRouteInfo<?, ?> route = (MethodBasedRouteInfo<?, ?>) route(router, "/element");
        AnnotationMetadata annotations = route.getAnnotationMetadata();
        assertTrue(annotations.hasAnnotation(Marker.class));
        assertTrue(annotations.hasAnnotation(Other.class));
        assertEquals("1", annotations.stringValue(Version.class).orElseThrow());
        assertSame(element, route.getAnnotationMetadataProvider().orElseThrow());
        assertEquals("2", route(router, "/override").getAnnotationMetadata().stringValue(Version.class).orElseThrow());
        assertTrue(route(router, "/override").getAnnotationMetadata().hasAnnotation(Other.class));
    }

    @Test
    void theRoutesOfAGroupHaveTheElementOfItsInnermostGroupUnlessTheyHaveTheirOwn() {
        AnnotationMetadataProvider outer = element(Version.class.getName(), Map.of("value", "1"), Other.class.getName());
        AnnotationMetadataProvider inner = element(Version.class.getName(), Map.of("value", "2"));
        AnnotationMetadataProvider own = element(Version.class.getName(), Map.of("value", "3"));
        Router router = router(routes -> routes.path("/g", group -> {
            // declared before the element of the group: the position in the lambda does not matter
            group.GET("/before", (request, pathVariables) -> HttpResponse.ok());
            group.annotationMetadata(outer);
            group.GET("/own", (request, pathVariables) -> HttpResponse.ok()).annotationMetadata(own);
            group.GET("/annotated", (request, pathVariables) -> HttpResponse.ok())
                .annotate(AnnotationValue.builder(Version.class).value("4").build());
            group.path("/nested", nested -> {
                nested.GET("/ping", (request, pathVariables) -> HttpResponse.ok());
                nested.annotationMetadata(inner);
            });
            group.path("/plain", plain -> plain.GET("/ping", (request, pathVariables) -> HttpResponse.ok()));
        }));
        for (String path : List.of("/g/before", "/g/plain/ping")) {
            MethodBasedRouteInfo<?, ?> route = (MethodBasedRouteInfo<?, ?>) route(router, path);
            assertSame(outer, route.getAnnotationMetadataProvider().orElseThrow(), path);
            assertEquals("1", route.getAnnotationMetadata().stringValue(Version.class).orElseThrow(), path);
            assertTrue(route.getAnnotationMetadata().hasAnnotation(Other.class), path);
        }
        MethodBasedRouteInfo<?, ?> ownRoute = (MethodBasedRouteInfo<?, ?>) route(router, "/g/own");
        assertSame(own, ownRoute.getAnnotationMetadataProvider().orElseThrow());
        assertEquals("3", ownRoute.getAnnotationMetadata().stringValue(Version.class).orElseThrow());
        assertTrue(!ownRoute.getAnnotationMetadata().hasAnnotation(Other.class));
        // an annotation of the route overrides the element of its group
        assertEquals("4", route(router, "/g/annotated").getAnnotationMetadata().stringValue(Version.class).orElseThrow());
        MethodBasedRouteInfo<?, ?> nested = (MethodBasedRouteInfo<?, ?>) route(router, "/g/nested/ping");
        assertSame(inner, nested.getAnnotationMetadataProvider().orElseThrow());
        assertEquals("2", nested.getAnnotationMetadata().stringValue(Version.class).orElseThrow());
        assertTrue(!nested.getAnnotationMetadata().hasAnnotation(Other.class));
    }

    @Test
    void theAnnotationsOfAGroupOverrideTheElementOfTheGroup() {
        AnnotationMetadataProvider element = element(Version.class.getName(), Map.of("value", "1"), Other.class.getName());
        Router router = router(routes -> routes.group(group -> {
            group.annotate(AnnotationValue.builder(Version.class).value("5").build());
            group.annotationMetadata(element);
            group.GET("/ping", (request, pathVariables) -> HttpResponse.ok());
        }));
        AnnotationMetadata metadata = route(router, "/ping").getAnnotationMetadata();
        assertEquals("5", metadata.stringValue(Version.class).orElseThrow());
        assertTrue(metadata.hasAnnotation(Other.class));
    }

    @Test
    void aDeclaredRouteHasItsAnnotations() {
        RouteDeclaration declaration = RouteDeclaration.of(HttpMethod.GET, "/declared");
        Router router = router(routes -> routes.group(group -> {
            group.annotate(Marker.class);
            group.handle(declaration, (request, pathVariables) -> HttpResponse.ok())
                .annotate(AnnotationValue.builder(Version.class).value("4").build());
        }));
        AnnotationMetadata metadata = route(router, "/declared").getAnnotationMetadata();
        assertTrue(metadata.hasAnnotation(Marker.class));
        assertEquals("4", metadata.stringValue(Version.class).orElseThrow());
    }

    @Test
    void aGroupIsAnnotatedInItsLambdaOnly() {
        assertThrows(IllegalStateException.class, () -> router(routes -> {
            HttpRouteBuilder[] escaped = new HttpRouteBuilder[1];
            routes.group(group -> escaped[0] = group);
            ((io.micronaut.web.router.builder.HttpRouteGroup) escaped[0]).annotate(Marker.class);
        }));
    }

    @Test
    void theArgumentsAreRequired() {
        router(routes -> {
            var route = routes.GET("/x", (request, pathVariables) -> HttpResponse.ok());
            assertThrows(NullPointerException.class, () -> route.annotate((AnnotationValue<?>) null));
            assertThrows(NullPointerException.class, () -> route.annotate((Class<Marker>) null));
            assertThrows(NullPointerException.class, () -> route.annotate((String) null));
            assertThrows(NullPointerException.class, () -> route.annotate(Marker.class, null));
        });
    }

    private static AnnotationMetadataProvider element(String annotation, Map<CharSequence, Object> members, String... markers) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(annotation, members);
        for (String marker : markers) {
            metadata.addDeclaredAnnotation(marker, Map.of());
        }
        return new AnnotationMetadataProvider() {
            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                return metadata;
            }
        };
    }

    private static RouteInfo<?> route(Router router, String path) {
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(path));
        assertNotNull(match, path);
        return match.getRouteInfo();
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
}
