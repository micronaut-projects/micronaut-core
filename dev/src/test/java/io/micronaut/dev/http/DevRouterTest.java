package io.micronaut.dev.http;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.DefaultRouteBuilder;
import io.micronaut.web.router.DefaultRouter;
import io.micronaut.web.router.RouteBuilder;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routes registered within a generation are served by the running server, through the development router; outside
 * development mode the application's router is injected directly.
 */
class DevRouterTest {

    @Test
    void outsideDevelopmentModeTheServerRoutesThroughTheApplicationsRouterDirectly() throws IOException {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "DevRouterTest", "micronaut.server.port", -1))) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            assertFalse(context.containsBean(DevRouter.class));
            Router router = context.getBean(Router.class);
            assertSame(DefaultRouter.class, router.getClass());
            assertSame(router, context.getBean(RouteExecutor.class).getRouter());
            assertEquals("200 static", get(server, "/dev-router/static"));
        }
    }

    @Test
    void routesRegisteredAtRuntimeAreServedByTheRunningServer() throws IOException {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "DevRouterTest",
            "micronaut.server.port", -1,
            DevelopmentMode.PROPERTY, true))) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            DevRouter devRouter = context.getBean(DevRouter.class);
            assertSame(devRouter, context.getBean(Router.class));
            assertSame(devRouter, context.getBean(RouteExecutor.class).getRouter());
            assertInstanceOf(DefaultRouter.class, devRouter.current());
            Router first = devRouter.current();
            assertEquals("200 static", get(server, "/dev-router/static"));
            assertEquals("200 fn", get(server, "/dev-router/fn"));
            assertTrue(get(server, "/dev-router/runtime").startsWith("404 "));

            // an HttpRoutes bean registered at runtime
            context.registerSingleton(HttpRoutes.class, routes -> routes.GET("/dev-router/runtime", (request, variables) -> text("runtime")));
            assertEquals(1, devRouter.rebuilds());
            assertEquals("200 runtime", get(server, "/dev-router/runtime"));
            assertEquals("200 static", get(server, "/dev-router/static"));
            assertEquals("200 fn", get(server, "/dev-router/fn"));
            assertFalse(first == devRouter.current(), "a new router");

            // a route builder registered at runtime
            Greeter greeter = context.getBean(Greeter.class);
            RouteBuilder builder = new DefaultRouteBuilder(context) {
                {
                    GET("/dev-router/builder", greeter, "greet");
                }
            };
            context.registerSingleton(RouteBuilder.class, builder);
            assertEquals(2, devRouter.rebuilds());
            assertEquals("200 greeted", get(server, "/dev-router/builder"));
            assertEquals("200 runtime", get(server, "/dev-router/runtime"));

            // the server, the executor and the context never changed
            assertSame(server, context.getBean(EmbeddedServer.class));
            assertTrue(server.isRunning());
            assertSame(devRouter, context.getBean(RouteExecutor.class).getRouter());
            // the ports the server applied reach every new router
            assertEquals("200 static", get(server, "/dev-router/static"));
        }
    }

    @Test
    void anExplicitRebuildServesTheSameRoutesFromANewTable() throws IOException {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "DevRouterTest",
            "micronaut.server.port", -1,
            DevelopmentMode.PROPERTY, true))) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            DevRouter devRouter = context.getBean(DevRouter.class);
            Router first = devRouter.current();
            List<String> before = routes(devRouter);
            devRouter.rebuild();
            assertFalse(first == devRouter.current(), "a new router");
            assertEquals(1, devRouter.rebuilds());
            assertEquals("200 static", get(server, "/dev-router/static"));
            assertEquals("200 fn", get(server, "/dev-router/fn"));
            // the same routes, each once: the builders recreated did not keep the routes of the previous table
            assertEquals(before, routes(devRouter));
        }
    }

    @Test
    void aRebuildThatFailsLeavesTheRoutesThatWereServed() throws IOException {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "DevRouterTest",
            "micronaut.server.port", -1,
            DevelopmentMode.PROPERTY, true))) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            DevRouter devRouter = context.getBean(DevRouter.class);
            assertEquals("200 static", get(server, "/dev-router/static"));

            // routes that cannot be declared: no router can be built while they are there
            context.registerSingleton(HttpRoutes.class, routes -> {
                throw new IllegalStateException("broken routes");
            });
            assertEquals(0, devRouter.rebuilds());
            assertEquals("200 static", get(server, "/dev-router/static"));
            assertEquals("200 fn", get(server, "/dev-router/fn"));
            assertTrue(server.isRunning());
        }
    }

    @Test
    void anApplicationThatDeclaresAPrimaryRouterKeepsIt() throws IOException {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "DevRouterTest",
            "dev-router.primary", true,
            "micronaut.server.port", -1,
            DevelopmentMode.PROPERTY, true))) {
            EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
            assertFalse(context.containsBean(DevRouter.class));
            assertSame(PrimaryRouter.class, context.getBean(Router.class).getClass());
            assertSame(context.getBean(Router.class), context.getBean(RouteExecutor.class).getRouter());
            assertEquals("200 static", get(server, "/dev-router/static"));
        }
    }

    private static List<String> routes(Router router) {
        return router.uriRoutes().map(route -> route.getHttpMethodName() + " " + route.getUriMatchTemplate().toPathString()).sorted().toList();
    }

    private static HttpResponse<String> text(String body) {
        return HttpResponse.ok(body).contentType("text/plain");
    }

    private static String get(EmbeddedServer server, String path) throws IOException {
        try (Socket socket = new Socket("localhost", server.getPort())) {
            socket.setSoTimeout(30_000);
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String status = response.substring("HTTP/1.1 ".length(), response.indexOf('\r'));
            return status.substring(0, 3) + " " + response.substring(response.indexOf("\r\n\r\n") + 4);
        }
    }

    @Requires(property = "spec.name", value = "DevRouterTest")
    @Controller("/dev-router")
    static class StaticController {
        @Get(value = "/static", produces = "text/plain")
        String hello() {
            return "static";
        }
    }

    @Requires(property = "spec.name", value = "DevRouterTest")
    @Singleton
    static class FnRoutes implements HttpRoutes {
        @Override
        public void routes(io.micronaut.web.router.builder.HttpRouteBuilder routes) {
            routes.GET("/dev-router/fn", (request, variables) -> text("fn"));
        }
    }

    @Requires(property = "spec.name", value = "DevRouterTest")
    @Requires(property = "dev-router.primary")
    @io.micronaut.context.annotation.Primary
    @Singleton
    static class PrimaryRouter extends DefaultRouter {
        PrimaryRouter(java.util.Collection<RouteBuilder> builders) {
            super(builders);
        }
    }

    @Requires(property = "spec.name", value = "DevRouterTest")
    @Singleton
    static class Greeter {
        @Executable
        public HttpResponse<String> greet() {
            return text("greeted");
        }
    }
}
