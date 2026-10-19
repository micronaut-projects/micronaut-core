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

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Status;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.executor.ThreadSelection;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The route info of the match of a located route has the annotations of the groups of the
 * locator routes, like the route info of a route declared in those groups: what the server
 * derives from the annotations of the route info, such as its {@code @Produces}, {@code @Status},
 * {@code @Consumes}, the binding of a {@link FilterMatcher} filter and its executor, derives from
 * them too, unless the located route has its own.
 */
class LocatedRouteInheritedRouteInfoTest {

    @Test
    void theRouteInfoOfALocatedRouteHasTheAnnotationsOfTheGroup() {
        Router router = router(null, routes -> routes.path("/group", group -> {
            group.annotate(Audited.class);
            group.annotate(produces(MediaType.TEXT_PLAIN));
            group.annotate(AnnotationValue.builder(Status.class).value(HttpStatus.CREATED).build());
            group.GET("/direct", (request, pathVariables) -> HttpResponse.ok());
            group.locate("/{id}", (request, pathVariables) -> "order", target -> items());
        }));

        for (String path : List.of("/group/direct", "/group/1/item")) {
            RouteInfo<Object> route = route(router, HttpRequest.GET(path));
            // what the binding of a @FilterMatcher filter checks
            assertTrue(route.getAnnotationMetadata().hasStereotype(Audited.class), path);
            assertEquals(List.of(MediaType.TEXT_PLAIN_TYPE), route.getProduces(), path);
            assertEquals(HttpStatus.CREATED, route.findStatus(null), path);
        }
    }

    @Test
    void theAnnotationsOfTheLocatedRouteOverrideTheOnesOfTheGroup() {
        Router router = router(null, routes -> routes.path("/group", group -> {
            group.annotate(produces(MediaType.TEXT_PLAIN));
            group.annotate(AnnotationValue.builder(Status.class).value(HttpStatus.CREATED).build());
            group.GET("/own")
                .annotate(produces(MediaType.TEXT_HTML))
                .annotate(AnnotationValue.builder(Status.class).value(HttpStatus.ACCEPTED).build())
                .handle((request, pathVariables) -> HttpResponse.ok());
            group.locate("/{id}", (request, pathVariables) -> "order", target -> items());
        }));

        RouteInfo<Object> direct = route(router, HttpRequest.GET("/group/own"));
        RouteInfo<Object> located = route(router, HttpRequest.GET("/group/1/own"));
        for (RouteInfo<Object> own : List.of(direct, located)) {
            // the route's own media type first, as for a route declared in the group
            assertEquals(MediaType.TEXT_HTML_TYPE, own.getProduces().get(0));
            assertEquals(HttpStatus.ACCEPTED, own.findStatus(null));
        }
        assertEquals(direct.getProduces(), located.getProduces());
    }

    @Test
    void theProducesOfTheGroupSelectsTheLocatedRoute() {
        Router router = router(null, routes -> routes.path("/group", group -> {
            group.annotate(produces(MediaType.TEXT_PLAIN));
            group.locate("/{id}", (request, pathVariables) -> "order", target -> items());
        }));

        assertNull(router.findClosest(HttpRequest.GET("/group/1/item").accept(MediaType.APPLICATION_JSON_TYPE)));
        assertNotNull(router.findClosest(HttpRequest.GET("/group/1/item").accept(MediaType.TEXT_PLAIN_TYPE)));
        assertFalse(router.findAny(HttpRequest.GET("/group/1/item").accept(MediaType.APPLICATION_JSON_TYPE)).isEmpty(),
            "the route still matches the path: the server answers 406");
    }

    @Test
    void theProducesOfTheLocatedRouteSelectsItOverTheOneOfTheGroup() {
        // the locator route does not select by the annotations of its group: the located routes do
        Router router = router(null, routes -> routes.path("/group", group -> {
            group.annotate(produces(MediaType.TEXT_PLAIN));
            group.GET("/own").annotate(produces(MediaType.TEXT_HTML)).handle((request, pathVariables) -> HttpResponse.ok());
            group.locate("/{id}", (request, pathVariables) -> "order", target -> items());
        }));

        for (String path : List.of("/group/own", "/group/1/own")) {
            assertNotNull(router.findClosest(HttpRequest.GET(path).accept(MediaType.TEXT_HTML_TYPE)), path);
            assertNull(router.findClosest(HttpRequest.GET(path).accept(MediaType.APPLICATION_JSON_TYPE)), path);
        }
    }

    @Test
    void theConsumesOfTheGroupSelectsALocatedRouteThatConsumesAll() {
        // a handler route consumes JSON unless it consumes all: only then do its annotations say what it consumes
        Router router = router(null, routes -> routes.path("/group", group -> {
            group.annotate(AnnotationValue.builder(Consumes.class).member("value", new String[]{MediaType.TEXT_PLAIN}).build());
            group.POST("/direct").consumesAll().handle((request, pathVariables) -> HttpResponse.ok());
            group.locate("/{id}", (request, pathVariables) -> "order", target -> items());
        }));

        for (String path : List.of("/group/direct", "/group/1/any")) {
            assertNotNull(router.findClosest(HttpRequest.POST(path, "text").contentType(MediaType.TEXT_PLAIN_TYPE)), path);
            assertNull(router.findClosest(HttpRequest.POST(path, "{}").contentType(MediaType.APPLICATION_JSON_TYPE)), path);
        }
    }

    @Test
    void theExecuteOnOfTheGroupSelectsTheExecutorOfTheLocatedRoute() {
        try (ApplicationContext context = ApplicationContext.run()) {
            ExecutorService located = Executors.newSingleThreadExecutor();
            try {
                context.registerSingleton(ExecutorService.class, located, Qualifiers.byName("located"));
                Router router = router(context, routes -> routes.path("/group", group -> {
                    group.annotate(AnnotationValue.builder(ExecuteOn.class).value("located").build());
                    group.GET("/direct", (request, pathVariables) -> HttpResponse.ok());
                    group.locate("/{id}", (request, pathVariables) -> "order", target -> items());
                }));
                for (String path : List.of("/group/direct", "/group/1/item")) {
                    assertSame(located, route(router, HttpRequest.GET(path)).getExecutor(ThreadSelection.AUTO), path);
                }
            } finally {
                located.shutdown();
            }
        }
    }

    @Test
    void aLocationGivesEveryRequestTheSameRouteInfo() {
        LocatedRoutes<Object> lines = TestLocatedRoutes.of(located -> located.GET("/lines", (request, pathVariables) -> HttpResponse.ok()));
        LocatedRoutes<?> orders = TestLocatedRoutes.of(located -> located.locate("/items/{item}", (target, request, pathVariables) -> "item", lines));
        Router router = router(null, routes -> {
            routes.path("/audited", group -> {
                group.annotate(Audited.class);
                group.locate("/{id}", (request, pathVariables) -> "order", target -> orders);
            });
            routes.path("/plain", group -> group.locate("/{id}", (request, pathVariables) -> "order", target -> orders));
        });

        RouteInfo<Object> first = route(router, HttpRequest.GET("/audited/1/items/2/lines"));
        assertSame(first, route(router, HttpRequest.GET("/audited/3/items/4/lines")));
        assertTrue(first.getAnnotationMetadata().hasStereotype(Audited.class));

        RouteInfo<Object> plain = route(router, HttpRequest.GET("/plain/1/items/2/lines"));
        assertNotSame(first, plain);
        assertSame(plain, route(router, HttpRequest.GET("/plain/3/items/4/lines")));
        assertFalse(plain.getAnnotationMetadata().hasStereotype(Audited.class));
    }

    private static LocatedRoutes<Object> items() {
        return TestLocatedRoutes.of(located -> {
            located.GET("/item", (request, pathVariables) -> HttpResponse.ok());
            located.GET("/own")
                .annotate(produces(MediaType.TEXT_HTML))
                .annotate(AnnotationValue.builder(Status.class).value(HttpStatus.ACCEPTED).build())
                .handle((request, pathVariables) -> HttpResponse.ok());
            located.POST("/any").consumesAll().handle((request, pathVariables) -> HttpResponse.ok());
        });
    }

    private static RouteInfo<Object> route(Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
        return match.getRouteInfo();
    }

    private static AnnotationValue<Produces> produces(String mediaType) {
        return AnnotationValue.builder(Produces.class).member("value", new String[]{mediaType}).build();
    }

    private static Router router(ApplicationContext context, Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(context, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    /**
     * A {@link FilterMatcher} annotation, like the one of an audit filter.
     */
    @FilterMatcher
    @Retention(RetentionPolicy.RUNTIME)
    @interface Audited {
    }
}
