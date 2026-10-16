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
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseFactory;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.web.router.builder.DirectRouteBuilder;
import io.micronaut.web.router.builder.HttpDirectRoutes;
import io.micronaut.web.router.direct.DirectMatch;
import io.micronaut.web.router.direct.DirectRouteLookup;
import io.micronaut.web.router.direct.DirectRouteSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The responses prepared with {@link DirectRouteBuilder#prepare(HttpResponse)}: by a runtime that
 * prepares them, and by the router when the runtime does not.
 */
class DirectRoutesPreparedTest {

    static final String SPEC_NAME = "DirectRoutesPreparedTest";
    static final String PLAIN = "DirectRoutesPreparedTestPlain";

    @Test
    void aRuntimeThatPreparesTheResponsesIsGivenEachOnceAndTheRoutesAnswerWithItsInstances() {
        PreparingSupport.prepared.clear();
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC_NAME))) {
            DirectRouteLookup routes = context.getBean(DirectRouteLookup.class);
            // the prepared responses and the value of the constant route, when the routes were declared
            assertEquals(4, PreparingSupport.prepared.size(), PreparingSupport.prepared.toString());
            assertSame(Routes.ok, PreparingSupport.prepared.get(0));
            assertSame(Routes.notModified, PreparingSupport.prepared.get(1));

            // the function chooses between the instances
            assertSame(Routes.ok, respond(routes, request("/version")));
            assertSame(Routes.notModified, respond(routes, request("/version").header(HttpHeaders.IF_NONE_MATCH, "v1")));
            assertSame(Routes.ok, respond(routes, request("/version").header(HttpHeaders.IF_NONE_MATCH, "v0")));
            // a value the runtime prepared is the same instance for every request
            HttpResponse<?> constant = respond(routes, request("/constant"));
            assertSame(PreparingSupport.prepared.get(2), constant);
            assertSame(constant, respond(routes, request("/constant")));
            assertSame(Routes.bytes, PreparingSupport.prepared.get(3));
            assertEquals(4, PreparingSupport.prepared.size());
        }
    }

    @Test
    void aResponseNoRuntimePreparesIsACopyThatIsNeverChanged() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", PLAIN))) {
            DirectRouteLookup routes = context.getBean(DirectRouteLookup.class);
            HttpResponse<?> ok = Routes.ok;
            assertFalse(ok instanceof MutableHttpResponse<?>, ok.getClass().getName());
            assertEquals(200, ok.code());
            assertEquals("v1", ok.getHeaders().get(HttpHeaders.ETAG));
            assertEquals("yes", ok.getAttributes().get("prepared", String.class).orElse(null));
            // the text was encoded, and the array of the caller copied
            assertArrayEquals("current".getBytes(StandardCharsets.UTF_8), (byte[]) ok.body());
            assertArrayEquals("bytes".getBytes(StandardCharsets.UTF_8), (byte[]) Routes.bytes.body());

            // the accessors return copies
            ((byte[]) ok.body())[0] = 'X';
            ok.toMutableResponse().header("X-Changed", "yes").body("changed");
            ok.getAttributes().put("prepared", "no");
            assertEquals("current", text(ok));
            assertNull(ok.getHeaders().get("X-Changed"));
            assertEquals("yes", ok.getAttributes().get("prepared", String.class).orElse(null));

            // the function answers with the instance, which the runtime copies for each request
            assertSame(ok, respond(routes, request("/version")));
            assertSame(Routes.notModified, respond(routes, request("/version").header(HttpHeaders.IF_NONE_MATCH, "v1")));
            assertEquals(304, Routes.notModified.code());
            assertNull(Routes.notModified.body());
            MutableHttpResponse<?> first = ok.toMutableResponse();
            MutableHttpResponse<?> second = ok.toMutableResponse();
            assertNotSame(first, second);
            assertEquals("current", text(first));

            // an asynchronous route answers with it too
            DirectMatch.Async async = assertInstanceOf(DirectMatch.Async.class, routes.match(request("/version-async")));
            assertSame(ok, async.respondAsync(HttpResponseFactory.INSTANCE).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void aResponseIsPreparedOnlyWhileTheRoutesAreDeclared() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", PLAIN))) {
            context.getBean(DirectRouteLookup.class);
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> Routes.builder.prepare(HttpResponse.ok()));
            assertTrue(error.getMessage().contains("The direct routes are already built"), error.getMessage());
        }
    }

    private static DirectRoutesTest.TestRequest request(String path) {
        return new DirectRoutesTest.TestRequest("GET", path);
    }

    private static @Nullable HttpResponse<?> respond(DirectRouteLookup routes, DirectRoutesTest.TestRequest request) {
        return assertInstanceOf(DirectMatch.Sync.class, routes.match(request), request.toString()).respond(HttpResponseFactory.INSTANCE);
    }

    private static String text(HttpResponse<?> response) {
        return new String((byte[]) response.body(), StandardCharsets.UTF_8);
    }

    /**
     * A runtime that prepares a response as it is: it records the responses it is given.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PreparingSupport implements DirectRouteSupport {
        static final List<HttpResponse<?>> prepared = new CopyOnWriteArrayList<>();

        @Override
        public HttpResponse<?> prepare(HttpResponse<?> response) {
            prepared.add(response);
            return response;
        }
    }

    /**
     * A runtime that prepares nothing.
     */
    @Singleton
    @Requires(property = "spec.name", value = PLAIN)
    static class PlainSupport implements DirectRouteSupport {
    }

    @Singleton
    @Requires(property = "spec.name", pattern = "DirectRoutesPreparedTest(Plain)?")
    static class Routes implements HttpDirectRoutes {
        static volatile DirectRouteBuilder builder;
        static volatile HttpResponse<?> ok;
        static volatile HttpResponse<?> notModified;
        static volatile HttpResponse<?> bytes;

        @Override
        public void routes(DirectRouteBuilder routes) {
            builder = routes;
            MutableHttpResponse<String> current = HttpResponse.ok("current").header(HttpHeaders.ETAG, "v1");
            current.setAttribute("prepared", "yes");
            ok = routes.prepare(current);
            notModified = routes.prepare(HttpResponse.notModified().header(HttpHeaders.ETAG, "v1"));
            routes.GET("/version").respond(direct -> "v1".equals(direct.request().header(HttpHeaders.IF_NONE_MATCH)) ? notModified : ok);
            routes.GET("/version-async").respondAsync(direct -> CompletableFuture.completedFuture(ok));
            routes.GET("/constant", HttpResponse.ok("constant"));
            byte[] array = "bytes".getBytes(StandardCharsets.UTF_8);
            bytes = routes.prepare(HttpResponse.ok(array));
            // the caller keeps its array
            array[0] = 'X';
        }
    }
}
