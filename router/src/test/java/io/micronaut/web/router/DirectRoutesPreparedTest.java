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
import io.micronaut.web.router.builder.DirectRouteBuilder;
import io.micronaut.web.router.builder.HttpDirectRoutes;
import io.micronaut.web.router.builder.RequestCondition;
import io.micronaut.web.router.builder.ValueMatcher;
import io.micronaut.web.router.direct.DirectMatch;
import io.micronaut.web.router.direct.DirectRouteLookup;
import io.micronaut.web.router.direct.DirectRouteSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The responses given as values to direct routes, prepared by the runtime once, when the routes
 * are declared, and chosen by the conditions of the routes.
 */
class DirectRoutesPreparedTest {

    static final String SPEC_NAME = "DirectRoutesPreparedTest";

    @Test
    void aRuntimeThatPreparesTheValuesIsGivenEachOnceAndTheRoutesAnswerWithItsInstances() {
        PreparingSupport.prepared.clear();
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC_NAME))) {
            DirectRouteLookup routes = context.getBean(DirectRouteLookup.class);
            // the values of the two routes, when the routes were declared
            assertEquals(2, PreparingSupport.prepared.size(), PreparingSupport.prepared.toString());
            HttpResponse<?> notModified = PreparingSupport.prepared.get(0);
            HttpResponse<?> current = PreparingSupport.prepared.get(1);
            assertEquals(304, notModified.code());
            assertEquals(200, current.code());

            // the conditions choose between the instances
            assertSame(current, respond(routes, request("/version")));
            assertSame(current, respond(routes, request("/version")));
            assertSame(notModified, respond(routes, request("/version").header(HttpHeaders.IF_NONE_MATCH, "\"v1\"")));
            assertSame(notModified, respond(routes, request("/version").header(HttpHeaders.IF_NONE_MATCH, "\"v0\", W/\"v1\"")));
            assertSame(current, respond(routes, request("/version").header(HttpHeaders.IF_NONE_MATCH, "\"v0\"")));
            assertEquals(2, PreparingSupport.prepared.size());
        }
    }

    private static DirectRoutesTest.TestRequest request(String path) {
        return new DirectRoutesTest.TestRequest("GET", path);
    }

    private static @Nullable HttpResponse<?> respond(DirectRouteLookup routes, DirectRoutesTest.TestRequest request) {
        return assertInstanceOf(DirectMatch.Sync.class, routes.match(request), request.toString()).respond(HttpResponseFactory.INSTANCE);
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

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpDirectRoutes {
        @Override
        public void routes(DirectRouteBuilder routes) {
            routes.GET("/version")
                .where(RequestCondition.header(HttpHeaders.IF_NONE_MATCH, ValueMatcher.entityTag("\"v1\"")))
                .order(-1)
                .respond(HttpResponse.notModified().header(HttpHeaders.ETAG, "\"v1\""));
            routes.GET("/version", HttpResponse.ok("current").header(HttpHeaders.ETAG, "\"v1\""));
        }
    }
}
