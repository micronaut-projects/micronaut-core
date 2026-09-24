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
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.executor.ThreadSelection;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteGroup;
import io.micronaut.web.router.builder.HttpRouteSpec;
import io.micronaut.web.router.builder.PathVariables;
import io.micronaut.web.router.builder.RouteDeclaration;
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The media types and the executor of a group of handler routes,
 * {@link HttpRouteGroup#consumes}, {@link HttpRouteGroup#produces} and
 * {@link HttpRouteGroup#executeOn}: the routes of the group and of its nested groups inherit them
 * unless they, or a nested group, set their own, wherever they are declared in the lambda.
 */
class RouteGroupSettingsTest {

    private static final List<MediaType> TEXT = List.of(MediaType.TEXT_PLAIN_TYPE);
    private static final List<MediaType> JSON = List.of(MediaType.APPLICATION_JSON_TYPE);
    private static final List<MediaType> XML = List.of(MediaType.APPLICATION_XML_TYPE);
    private static final List<MediaType> FORMS = List.of(MediaType.APPLICATION_FORM_URLENCODED_TYPE, MediaType.MULTIPART_FORM_DATA_TYPE);

    @Test
    void theRoutesOfAGroupConsumeAndProduceItsMediaTypes() {
        Router router = router(routes -> routes.path("/notes", notes -> {
            notes.consumes(MediaType.TEXT_PLAIN_TYPE).produces(MediaType.TEXT_PLAIN_TYPE);
            notes.POST("/", RouteGroupSettingsTest::ok);
            notes.PUT("/{id}", Argument.of(String.class), (request, pathVariables, body) -> HttpResponse.ok(body));
            notes.GET(RouteGroupSettingsTest::ok);
        }));

        UriRouteInfo<?, ?> post = route(router, HttpRequest.POST("/notes", "x").contentType(MediaType.TEXT_PLAIN_TYPE));
        assertEquals(TEXT, post.getConsumes());
        assertEquals(TEXT, post.getProduces());
        assertEquals(TEXT, route(router, HttpRequest.PUT("/notes/1", "x").contentType(MediaType.TEXT_PLAIN_TYPE)).getConsumes());
        // the group's media types replace the default application/json
        assertNull(router.findClosest(HttpRequest.POST("/notes", "{}").contentType(MediaType.APPLICATION_JSON_TYPE)));
        assertEquals(TEXT, route(router, HttpRequest.GET("/notes")).getProduces());
    }

    @Test
    void aRouteWithItsOwnMediaTypesReplacesTheOnesOfItsGroup() {
        Router router = router(routes -> routes.path("/notes", notes -> {
            notes.consumes(MediaType.TEXT_PLAIN_TYPE).produces(MediaType.TEXT_PLAIN_TYPE);
            notes.POST("/json", RouteGroupSettingsTest::ok).consumes(MediaType.APPLICATION_JSON_TYPE).produces(MediaType.APPLICATION_JSON_TYPE);
            notes.POST("/any", RouteGroupSettingsTest::ok).consumesAll();
        }));

        UriRouteInfo<?, ?> json = route(router, HttpRequest.POST("/notes/json", "{}").contentType(MediaType.APPLICATION_JSON_TYPE));
        assertEquals(JSON, json.getConsumes());
        assertEquals(JSON, json.getProduces());
        assertNull(router.findClosest(HttpRequest.POST("/notes/json", "x").contentType(MediaType.TEXT_PLAIN_TYPE)));
        UriRouteInfo<?, ?> any = route(router, HttpRequest.POST("/notes/any", "x").contentType(MediaType.IMAGE_PNG_TYPE));
        assertEquals(List.of(), any.getConsumes());
        // only the media types it set are its own: it produces the ones of the group
        assertEquals(TEXT, any.getProduces());
    }

    @Test
    void aGroupThatConsumesAllAcceptsAnyMediaType() {
        Router router = router(routes -> routes.path("/upload", upload -> {
            upload.consumesAll();
            upload.POST("/", RouteGroupSettingsTest::ok);
        }));
        assertEquals(List.of(), route(router, HttpRequest.POST("/upload", "x").contentType(MediaType.IMAGE_PNG_TYPE)).getConsumes());
    }

    @Test
    void aNestedGroupOverridesTheMediaTypesOfTheGroupsAroundIt() {
        Router router = router(routes -> routes.path("/outer", outer -> {
            outer.consumes(MediaType.TEXT_PLAIN_TYPE).produces(MediaType.TEXT_PLAIN_TYPE);
            outer.POST("/a", RouteGroupSettingsTest::ok);
            outer.path("/inner", inner -> {
                inner.consumes(MediaType.APPLICATION_XML_TYPE);
                inner.POST("/b", RouteGroupSettingsTest::ok);
                inner.path("/deepest", deepest -> deepest.POST("/c", RouteGroupSettingsTest::ok));
            });
        }));

        UriRouteInfo<?, ?> a = route(router, HttpRequest.POST("/outer/a", "x").contentType(MediaType.TEXT_PLAIN_TYPE));
        assertEquals(TEXT, a.getConsumes());
        UriRouteInfo<?, ?> b = route(router, HttpRequest.POST("/outer/inner/b", "x").contentType(MediaType.APPLICATION_XML_TYPE));
        assertEquals(XML, b.getConsumes());
        // the inner group sets what its routes consume only: they produce what the outer group produces
        assertEquals(TEXT, b.getProduces());
        UriRouteInfo<?, ?> c = route(router, HttpRequest.POST("/outer/inner/deepest/c", "x").contentType(MediaType.APPLICATION_XML_TYPE));
        assertEquals(XML, c.getConsumes());
        assertEquals(TEXT, c.getProduces());
    }

    @Test
    void theSettingsApplyToEveryRouteOfTheLambdaWhereverTheyAreDeclared() {
        Router router = router(routes -> routes.path("/late", late -> {
            late.POST("/before", RouteGroupSettingsTest::ok);
            late.path("/nested", nested -> nested.POST("/route", RouteGroupSettingsTest::ok));
            // after the routes and the nested group
            late.consumes(MediaType.TEXT_PLAIN_TYPE);
            late.executeOn(TaskExecutors.BLOCKING);
            late.POST("/after", RouteGroupSettingsTest::ok);
            late.produces(MediaType.TEXT_PLAIN_TYPE);
        }));

        for (String path : List.of("/late/before", "/late/nested/route", "/late/after")) {
            UriRouteInfo<?, ?> route = route(router, HttpRequest.POST(path, "x").contentType(MediaType.TEXT_PLAIN_TYPE));
            assertEquals(TEXT, route.getConsumes(), path);
            assertEquals(TEXT, route.getProduces(), path);
        }
    }

    @Test
    void aFormRouteConsumesFormsWhatTheGroupConsumes() {
        RouteDeclaration declared = RouteDeclaration.of(HttpMethod.POST, "/forms/declared");
        Router router = router(routes -> routes.group(forms -> {
            forms.consumes(MediaType.TEXT_PLAIN_TYPE).produces(MediaType.TEXT_PLAIN_TYPE);
            forms.handleForm(HttpMethod.POST, "/forms/form", (request, pathVariables, form) -> HttpResponse.ok());
            forms.handleForm("PROPPATCH", "/forms/custom", (request, pathVariables, form) -> HttpResponse.ok());
            forms.handleForm(declared, (request, pathVariables, form) -> HttpResponse.ok());
            forms.handleForm(HttpMethod.PUT, "/forms/json", (request, pathVariables, form) -> HttpResponse.ok()).consumes(MediaType.APPLICATION_JSON_TYPE);
        }));

        UriRouteInfo<?, ?> form = route(router, HttpRequest.POST("/forms/form", "a=b").contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
        assertEquals(FORMS, form.getConsumes());
        // the form route produces what the group produces
        assertEquals(TEXT, form.getProduces());
        assertEquals(FORMS, route(router, HttpRequest.create(HttpMethod.CUSTOM, "/forms/custom", "PROPPATCH")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)).getConsumes());
        assertEquals(FORMS, route(router, HttpRequest.POST("/forms/declared", "a=b").contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)).getConsumes());
        // its own media types still win
        assertEquals(JSON, route(router, HttpRequest.PUT("/forms/json", "{}").contentType(MediaType.APPLICATION_JSON_TYPE)).getConsumes());
    }

    @Test
    void everyKindOfHandlerRouteOfTheGroupInheritsTheSettings() {
        RouteDeclaration declared = RouteDeclaration.of(HttpMethod.GET, "/kinds/declared");
        Router router = router(routes -> routes.group(kinds -> {
            kinds.produces(MediaType.TEXT_PLAIN_TYPE);
            kinds.handle(Set.of(HttpMethod.PUT, HttpMethod.PATCH), "/kinds/both", RouteGroupSettingsTest::ok);
            kinds.handle(declared, RouteGroupSettingsTest::ok);
            kinds.asyncGET("/kinds/async", (request, pathVariables) -> java.util.concurrent.CompletableFuture.completedFuture(HttpResponse.ok()));
            kinds.GET("/kinds/get", RouteGroupSettingsTest::ok);
            kinds.handle("PROPFIND", "/kinds/custom", RouteGroupSettingsTest::ok);
            kinds.path("/kinds/pathless", pathless -> pathless.GET(RouteGroupSettingsTest::ok));
        }));

        assertEquals(TEXT, route(router, HttpRequest.PUT("/kinds/both", "{}")).getProduces());
        assertEquals(TEXT, route(router, HttpRequest.PATCH("/kinds/both", "{}")).getProduces());
        assertEquals(TEXT, route(router, HttpRequest.GET("/kinds/declared")).getProduces());
        // the implicit HEAD route of the declared route and of the GET route
        assertEquals(TEXT, route(router, HttpRequest.HEAD("/kinds/declared")).getProduces());
        assertEquals(TEXT, route(router, HttpRequest.HEAD("/kinds/get")).getProduces());
        assertEquals(TEXT, route(router, HttpRequest.GET("/kinds/async")).getProduces());
        assertEquals(TEXT, route(router, HttpRequest.create(HttpMethod.CUSTOM, "/kinds/custom", "PROPFIND")).getProduces());
        assertEquals(TEXT, route(router, HttpRequest.GET("/kinds/pathless")).getProduces());
    }

    @Test
    void aRouteChangedAfterTheGroupClosedKeepsItsOwnValue() {
        HttpRouteSpec[] kept = new HttpRouteSpec[1];
        Router router = router(routes -> {
            routes.group(group -> {
                group.produces(MediaType.TEXT_PLAIN_TYPE);
                kept[0] = group.GET("/kept", RouteGroupSettingsTest::ok);
            });
            kept[0].produces(MediaType.APPLICATION_XML_TYPE);
        });
        assertEquals(XML, route(router, HttpRequest.GET("/kept")).getProduces());
    }

    @Test
    void theRoutesOfALocatedTableAndOfItsGroupsHaveTheirOwnSettings() {
        RouteTableFactory tables = new RouteTableFactory(ExecutionHandleLocator.EMPTY, new HyphenatedUriNamingStrategy(), ConversionService.SHARED, null);
        RouteTable table = tables.buildLocatedHttpRoutes(String.class, located -> {
            located.GET("/plain", RouteGroupSettingsTest::ok);
            located.group(group -> {
                group.produces(MediaType.TEXT_PLAIN_TYPE);
                group.GET("/grouped", (request, pathVariables) -> HttpResponse.ok(pathVariables.locatedTarget(String.class)));
            });
        });
        // the media types of the group of the locator route are not the ones of the located table
        Router router = router(routes -> routes.path("/located", group -> {
            group.produces(MediaType.APPLICATION_XML_TYPE).consumes(MediaType.APPLICATION_XML_TYPE);
            group.locate("/{id}", (request, pathVariables) -> pathVariables.getString("id"), target -> table);
        }));

        assertEquals(TEXT, route(router, HttpRequest.GET("/located/1/grouped")).getProduces());
        UriRouteInfo<?, ?> plain = route(router, HttpRequest.GET("/located/1/plain"));
        assertEquals(JSON, plain.getConsumes());
        assertEquals(route(router(routes -> routes.GET("/plain", RouteGroupSettingsTest::ok)), HttpRequest.GET("/plain")).getProduces(),
            plain.getProduces());
    }

    @Test
    void theRoutesOfAGroupRunOnItsExecutorUnlessTheyChooseTheirThread() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("micronaut.server.thread-selection", "AUTO"))) {
            ExecutorService blocking = context.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.BLOCKING));
            ExecutorService io = context.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.IO));
            Router router = router(context, routes -> routes.path("/threads", threads -> {
                threads.GET("/group", RouteGroupSettingsTest::ok);
                threads.GET("/event-loop", RouteGroupSettingsTest::ok).nonBlocking();
                threads.GET("/io", RouteGroupSettingsTest::ok).executeOn(TaskExecutors.IO);
                threads.path("/non-blocking", nonBlocking -> {
                    nonBlocking.nonBlocking();
                    nonBlocking.GET("/nested", RouteGroupSettingsTest::ok);
                    nonBlocking.GET("/io", RouteGroupSettingsTest::ok).executeOn(TaskExecutors.IO);
                });
                threads.path("/inherited", inherited -> inherited.GET("/nested", RouteGroupSettingsTest::ok));
                // after the routes
                threads.executeOn(TaskExecutors.BLOCKING);
            }));

            assertSame(blocking, executor(router, "/threads/group"));
            assertSame(blocking, executor(router, "/threads/inherited/nested"));
            assertSame(blocking, executor(router, "/threads/group", HttpMethod.HEAD));
            assertNull(executor(router, "/threads/event-loop"), "the route's nonBlocking() wins");
            assertSame(io, executor(router, "/threads/io"));
            assertNull(executor(router, "/threads/non-blocking/nested"), "the nested group's nonBlocking() wins");
            assertSame(io, executor(router, "/threads/non-blocking/io"));
        }
    }

    @Test
    void theLastExecutorChoiceOfAGroupWins() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("micronaut.server.thread-selection", "AUTO"))) {
            ExecutorService io = context.getBean(ExecutorService.class, Qualifiers.byName(TaskExecutors.IO));
            Router router = router(context, routes -> {
                routes.path("/a", group -> {
                    group.executeOn(TaskExecutors.IO).nonBlocking();
                    group.GET("/route", RouteGroupSettingsTest::ok);
                });
                routes.path("/b", group -> {
                    group.nonBlocking().executeOn(TaskExecutors.IO);
                    group.GET("/route", RouteGroupSettingsTest::ok);
                });
            });
            assertNull(executor(router, "/a/route"));
            assertSame(io, executor(router, "/b/route"));
        }
    }

    @Test
    void aClosedGroupRejectsSettingsAndInvalidSettingsAreRejected() {
        HttpRouteGroup[] kept = new HttpRouteGroup[1];
        router(routes -> routes.group(group -> kept[0] = group));
        HttpRouteGroup closed = kept[0];
        assertThrows(IllegalStateException.class, () -> closed.consumes(MediaType.TEXT_PLAIN_TYPE));
        assertThrows(IllegalStateException.class, closed::consumesAll);
        assertThrows(IllegalStateException.class, () -> closed.produces(MediaType.TEXT_PLAIN_TYPE));
        assertThrows(IllegalStateException.class, () -> closed.executeOn(TaskExecutors.BLOCKING));
        assertThrows(IllegalStateException.class, closed::nonBlocking);

        router(routes -> routes.group(group -> {
            assertThrows(NullPointerException.class, () -> group.consumes((MediaType[]) null));
            assertThrows(NullPointerException.class, () -> group.produces(MediaType.TEXT_PLAIN_TYPE, null));
            assertThrows(NullPointerException.class, () -> group.executeOn(null));
            assertThrows(IllegalArgumentException.class, () -> group.executeOn(" "));
        }));
    }

    private static @Nullable ExecutorService executor(Router router, String path) {
        return executor(router, path, HttpMethod.GET);
    }

    private static @Nullable ExecutorService executor(Router router, String path, HttpMethod method) {
        return route(router, HttpRequest.create(method, path)).getExecutor(ThreadSelection.AUTO);
    }

    private static UriRouteInfo<?, ?> route(Router router, MutableHttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getMethodName() + " " + request.getPath());
        return match.getRouteInfo();
    }

    private static HttpResponse<?> ok(HttpRequest<?> request, PathVariables pathVariables) {
        return HttpResponse.ok();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        return router(null, routes);
    }

    private static Router router(@Nullable ApplicationContext context, Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(context, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
