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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.DefaultRouteBuilder;
import io.micronaut.web.router.DefaultRouter;
import io.micronaut.web.router.RouteSource;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Routes resolved at runtime by a {@link RouteSource} are real routes: filters apply to them,
 * controllers take precedence, a wrong method is answered with 405, and they can change while the
 * server runs.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class RouteSourceTest {
    public static final String SPEC_NAME = "RouteSourceTest";

    @Test
    void dynamicRouteIsHandledAndFiltered() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/dynamic/a/b"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("dynamic /dynamic/a/b")
                .headers(Map.of("X-Dynamic-Filter", "true"))
                .build());
        }
    }

    @Test
    void controllerRouteTakesPrecedence() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/dynamic/static"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("static")
                .build());
        }
    }

    @Test
    void wrongMethodIsNotAllowed() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.DELETE("/dynamic/a"), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .build());
        }
    }

    @Test
    void routesChangeAtRuntime() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/added/x"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());

            server.getApplicationContext().getBean(DynamicRoutes.class).setUris(List.of("/dynamic/{+path}", "/added/{+path}"));

            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/added/x"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("dynamic /added/x")
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class DynamicHandler {
        @Executable
        HttpResponse<String> handle(HttpRequest<?> request) {
            return HttpResponse.ok("dynamic " + request.getPath()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class DynamicRoutes implements RouteSource {
        private final ApplicationContext applicationContext;
        private final DynamicHandler handler;
        private final AtomicReference<Router> router = new AtomicReference<>();
        private final RouteSource delegate = RouteSource.of(router::get);

        DynamicRoutes(ApplicationContext applicationContext, DynamicHandler handler) {
            this.applicationContext = applicationContext;
            this.handler = handler;
            setUris(List.of("/dynamic/{+path}"));
        }

        void setUris(List<String> uris) {
            DefaultRouteBuilder builder = new DefaultRouteBuilder(applicationContext) {
            };
            for (String uri : uris) {
                builder.GET(uri, handler, "handle", HttpRequest.class);
            }
            router.set(new DefaultRouter(builder));
        }

        @Override
        public <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request) {
            return delegate.findAllClosest(request);
        }

        @Override
        public <T, R> List<UriRouteMatch<T, R>> findAny(HttpRequest<?> request) {
            return delegate.findAny(request);
        }
    }

    @ServerFilter("/dynamic/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class DynamicFilter {
        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Dynamic-Filter", "true");
        }
    }

    @Controller("/dynamic/static")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class StaticController {
        @Get(produces = MediaType.TEXT_PLAIN)
        String get() {
            return "static";
        }
    }
}
