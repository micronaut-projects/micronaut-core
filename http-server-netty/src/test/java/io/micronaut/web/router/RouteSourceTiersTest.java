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
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The router consults the application routes and then the tables of the route sources, each a
 * tier: a tier without a route for the method, the content type or the accepted types of a request
 * falls through to the next one, and the {@code 405}, {@code 415} and {@code 406} answers take the
 * routes of every tier into account.
 */
class RouteSourceTiersTest {
    private static final String SPEC_NAME = "RouteSourceTiersTest";

    private ApplicationContext context;

    @BeforeEach
    void start() {
        context = ApplicationContext.run(Map.of("spec.name", SPEC_NAME));
    }

    @AfterEach
    void stop() {
        context.close();
    }

    @Test
    void aTierWithoutTheMethodFallsThroughAndTheAllowedMethodsAreOfEveryTier() {
        try (EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
             HttpClient httpClient = context.createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient client = httpClient.toBlocking();

            assertEquals("controller x", client.retrieve(HttpRequest.GET("/tiers/x")));
            // the application only has a GET route for the path: the POST route of the table serves it
            assertEquals("table POST /tiers/x", client.retrieve(HttpRequest.POST("/tiers/x", "")));

            HttpClientResponseException notAllowed = assertThrows(HttpClientResponseException.class,
                () -> client.exchange(HttpRequest.DELETE("/tiers/x")));
            assertEquals(HttpStatus.METHOD_NOT_ALLOWED, notAllowed.getStatus());
            assertEquals(Set.of("GET", "HEAD", "POST"), allowed(notAllowed.getResponse()));

            // a path only a table has
            HttpClientResponseException tableNotAllowed = assertThrows(HttpClientResponseException.class,
                () -> client.exchange(HttpRequest.PUT("/table-only/x", "")));
            assertEquals(HttpStatus.METHOD_NOT_ALLOWED, tableNotAllowed.getStatus());
            assertEquals(Set.of("GET", "HEAD"), allowed(tableNotAllowed.getResponse()));

            HttpClientResponseException notFound = assertThrows(HttpClientResponseException.class,
                () -> client.exchange(HttpRequest.DELETE("/nowhere/x")));
            assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());
        }
    }

    @Test
    void aTierThatDoesNotConsumeTheContentTypeFallsThrough() {
        try (EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
             HttpClient httpClient = context.createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient client = httpClient.toBlocking();

            assertEquals("controller text", client.retrieve(HttpRequest.POST("/media", "text").contentType(MediaType.TEXT_PLAIN_TYPE)));
            assertEquals("table {}", client.retrieve(HttpRequest.POST("/media", "{}").contentType(MediaType.APPLICATION_JSON_TYPE)));

            // neither tier consumes it
            HttpClientResponseException unsupported = assertThrows(HttpClientResponseException.class,
                () -> client.exchange(HttpRequest.POST("/media", "<x/>").contentType(MediaType.APPLICATION_XML_TYPE)));
            assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, unsupported.getStatus());
        }
    }

    @Test
    void aTierThatDoesNotProduceAnAcceptedTypeFallsThrough() {
        try (EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
             HttpClient httpClient = context.createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient client = httpClient.toBlocking();

            assertEquals("controller text", client.retrieve(HttpRequest.GET("/negotiate").accept(MediaType.TEXT_PLAIN_TYPE)));
            assertEquals("{\"source\":\"table\"}", client.retrieve(HttpRequest.GET("/negotiate").accept(MediaType.APPLICATION_JSON_TYPE)));

            // neither tier produces it
            HttpClientResponseException notAcceptable = assertThrows(HttpClientResponseException.class,
                () -> client.exchange(HttpRequest.GET("/negotiate").accept(MediaType.APPLICATION_XML_TYPE)));
            assertEquals(HttpStatus.NOT_ACCEPTABLE, notAcceptable.getStatus());
        }
    }

    @Test
    void aTableReplacedBetweenRequestsServesTheNextRequest() {
        Routes routes = context.getBean(Routes.class);
        try (EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
             HttpClient httpClient = context.createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient client = httpClient.toBlocking();

            routes.replace(r -> r.GET("/replaced/{name}", Handler.class, "first", HttpRequest.class));
            assertEquals("first /replaced/x", client.retrieve(HttpRequest.GET("/replaced/x")));

            routes.replace(r -> r.GET("/replaced/{name}", Handler.class, "second", HttpRequest.class));
            assertEquals("second /replaced/x", client.retrieve(HttpRequest.GET("/replaced/x")));

            // the route is gone: the path is not found, and a method of another route is not allowed
            routes.replace(r -> r.POST("/replaced/{name}", Handler.class, "post", HttpRequest.class));
            HttpClientResponseException notAllowed = assertThrows(HttpClientResponseException.class,
                () -> client.exchange(HttpRequest.GET("/replaced/x")));
            assertEquals(HttpStatus.METHOD_NOT_ALLOWED, notAllowed.getStatus());
            assertEquals(Set.of("POST"), allowed(notAllowed.getResponse()));

            routes.replace(r -> { });
            HttpClientResponseException notFound = assertThrows(HttpClientResponseException.class,
                () -> client.exchange(HttpRequest.GET("/replaced/x")));
            assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());
        }
    }

    @Test
    void theRouterFallsThroughTheTiersForEveryLookup() {
        Router router = context.getBean(Router.class);

        // the application tier has the path, but not the method
        UriRouteMatch<Object, Object> post = router.findClosest(HttpRequest.POST("/tiers/x", ""));
        assertNotNull(post);
        assertEquals("post", post.getRouteInfo().getTargetMethod().getMethodName());
        assertEquals(1, router.findAllClosest(HttpRequest.POST("/tiers/x", "")).size());
        assertEquals(List.of("GET", "HEAD", "POST"), router.findAny(HttpRequest.DELETE("/tiers/x"))
            .stream().map(match -> match.getRouteInfo().getHttpMethodName()).sorted().toList());
        assertEquals(List.of("GET", "POST"), router.findAny("/tiers/x", null)
            .map(match -> match.getRouteInfo().getHttpMethodName()).filter(method -> !method.equals("HEAD")).sorted().toList());
        assertEquals(1, router.find(HttpMethod.POST, "/tiers/x", null).count());
        assertEquals("post", router.route(HttpMethod.POST, "/tiers/x").orElseThrow().getRouteInfo().getTargetMethod().getMethodName());
        assertNull(router.findClosest(HttpRequest.DELETE("/tiers/x")));
    }

    @Test
    void theDefaultPortsBelongToTheRouterNotToTheTable() {
        RouteTable table = context.getBean(RouteTableFactory.class)
            .build(r -> r.GET("/ported/{name}", Handler.class, "first", HttpRequest.class));
        DefaultRouter onFirstPort = DefaultRouter.withRouteSources(List.of(), () -> List.of(() -> table), List::of);
        DefaultRouter onSecondPort = DefaultRouter.withRouteSources(List.of(), () -> List.of(() -> table), List::of);
        onFirstPort.applyDefaultPorts(List.of(8080));
        onSecondPort.applyDefaultPorts(List.of(9090));

        // one table, two routers: each matches it on its own ports
        assertNotNull(onFirstPort.findClosest(onPort(HttpRequest.GET("/ported/x"), 8080)));
        assertNull(onFirstPort.findClosest(onPort(HttpRequest.GET("/ported/x"), 9090)));
        assertNotNull(onSecondPort.findClosest(onPort(HttpRequest.GET("/ported/x"), 9090)));
        assertNull(onSecondPort.findClosest(onPort(HttpRequest.GET("/ported/x"), 8080)));
    }

    private static Set<String> allowed(HttpResponse<?> response) {
        Set<String> methods = new TreeSet<>();
        for (String value : response.getHeaders().getAll(HttpHeaders.ALLOW)) {
            Arrays.stream(value.split(",")).map(String::trim).filter(method -> !method.isEmpty()).forEach(methods::add);
        }
        return methods;
    }

    private static HttpRequest<?> onPort(HttpRequest<Object> request, int port) {
        InetSocketAddress address = new InetSocketAddress("localhost", port);
        return new HttpRequestWrapper<>(request) {
            @Override
            public InetSocketAddress getServerAddress() {
                return address;
            }
        };
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TierController {
        @Get(value = "/tiers/{name}", produces = MediaType.TEXT_PLAIN)
        String tiers(String name) {
            return "controller " + name;
        }

        @Post(value = "/media", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String media(@Body String body) {
            return "controller " + body;
        }

        @Get(value = "/negotiate", produces = MediaType.TEXT_PLAIN)
        String negotiate() {
            return "controller text";
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Handler {
        @Executable
        HttpResponse<String> post(HttpRequest<?> request) {
            return HttpResponse.ok("table POST " + request.getPath()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }

        @Executable
        HttpResponse<String> first(HttpRequest<?> request) {
            return HttpResponse.ok("first " + request.getPath()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }

        @Executable
        HttpResponse<String> second(HttpRequest<?> request) {
            return HttpResponse.ok("second " + request.getPath()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }

        @Executable
        @Consumes(MediaType.APPLICATION_JSON)
        HttpResponse<String> json(@Body String body) {
            return HttpResponse.ok("table " + body).contentType(MediaType.TEXT_PLAIN_TYPE);
        }

        @Executable
        @Produces(MediaType.APPLICATION_JSON)
        HttpResponse<String> negotiate() {
            return HttpResponse.ok("{\"source\":\"table\"}").contentType(MediaType.APPLICATION_JSON_TYPE);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements RouteSource {
        private final RouteTableFactory tables;
        private volatile RouteTable current;

        Routes(RouteTableFactory tables) {
            this.tables = tables;
            reset();
        }

        void reset() {
            replace(routes -> {
                routes.POST("/tiers/{name}", Handler.class, "post", HttpRequest.class);
                routes.GET("/table-only/{name}", Handler.class, "first", HttpRequest.class);
                routes.POST("/media", Handler.class, "json", String.class);
                routes.GET("/negotiate", Handler.class, "negotiate");
            });
        }

        void replace(Consumer<RouteBuilder> routes) {
            current = tables.build(routes);
        }

        @Override
        public RouteTable snapshot() {
            return current;
        }
    }
}
