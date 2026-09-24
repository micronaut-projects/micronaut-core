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
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A router that replaces the default one and calls its public constructor still has the routes
 * of the route sources.
 */
class ReplacedRouterRouteSourcesTest {
    private static final String SPEC_NAME = "ReplacedRouterRouteSourcesTest";

    @Test
    void aReplacedRouterServesTheRoutesOfTheRouteSources() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC_NAME));
             EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
             HttpClient client = context.createBean(HttpClient.class, server.getURL())) {
            assertInstanceOf(ReplacedRouter.class, context.getBean(Router.class));

            assertEquals("source /from-source/x", client.toBlocking().retrieve(HttpRequest.GET("/from-source/x")));
        }
    }

    @Singleton
    @Replaces(DefaultRouter.class)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ReplacedRouter extends DefaultRouter {
        ReplacedRouter(Collection<RouteBuilder> builders) {
            super(builders);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Handler {
        @Executable
        HttpResponse<String> handle(HttpRequest<?> request) {
            return HttpResponse.ok("source " + request.getPath()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Source implements RouteSource {
        private final RouteTable table;

        Source(RouteTableFactory tables) {
            table = tables.build(routes -> routes.GET("/from-source/{+path}", Handler.class, "handle", HttpRequest.class));
        }

        @Override
        public RouteTable snapshot() {
            return table;
        }
    }
}
