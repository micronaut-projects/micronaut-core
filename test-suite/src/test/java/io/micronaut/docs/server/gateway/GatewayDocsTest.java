package io.micronaut.docs.server.gateway;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.gateway.GatewayObserver;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayDocsTest {

    @Test
    void theSamplesOfTheGatewayGuide() {
        int port = SocketUtils.findAvailableTcpPort();
        Map<String, Object> properties = new HashMap<>();
        properties.put("spec.name", "GatewayDocsTest");
        properties.put("micronaut.server.port", port);
        // the application is its own upstream: the routes of Upstream below
        properties.put("orders.url", "http://localhost:" + port);
        String r = "micronaut.gateway.routes.";
        properties.put(r + "audited.uri", "${orders.url}");
        properties.put(r + "audited.predicates", List.of("Path=/audited/**"));
        properties.put(r + "audited.filters", List.of("StripPrefix=1", "Audit=billing"));
        properties.put(r + "limited.uri", "${orders.url}");
        properties.put(r + "limited.predicates", List.of("Path=/limited/**"));
        properties.put(r + "limited.filters", List.of("StripPrefix=1", "RequestRateLimiter=1, 1, 1, tenant"));
        properties.put(r + "guarded.uri", "http://127.0.0.1:1");
        properties.put(r + "guarded.predicates", List.of("Path=/guarded/**"));
        properties.put(r + "guarded.filters", List.of("CircuitBreaker=orders, ordersFallback"));
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, properties);
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            var http = client.toBlocking();
            // routing by the body
            assertEquals("express", http.retrieve(HttpRequest.POST("/orders/1", "{\"express\":true}").contentType(MediaType.APPLICATION_JSON_TYPE)));
            assertEquals("standard", http.retrieve(HttpRequest.POST("/orders/1", "{}").contentType(MediaType.APPLICATION_JSON_TYPE)));
            // the routes of the locator
            assertEquals("tenant=acme team=-", http.retrieve(HttpRequest.GET("/tenants/acme/echo")));
            // a filter of the configuration made by a factory bean
            HttpResponse<String> audited = http.exchange(HttpRequest.GET("/audited/echo"), String.class);
            assertEquals("tenant=- team=billing", audited.body());
            assertEquals("audited", audited.getHeaders().get("X-Audited-Route"));
            // a rate limit by the key of a key resolver bean
            assertEquals(200, http.exchange(HttpRequest.GET("/limited/echo").header("X-Tenant", "a"), String.class).code());
            HttpClientResponseException tooMany = assertThrows(HttpClientResponseException.class,
                () -> http.exchange(HttpRequest.GET("/limited/echo").header("X-Tenant", "a"), String.class));
            assertEquals(429, tooMany.code());
            // a circuit breaker fallback
            assertTrue(http.retrieve(HttpRequest.GET("/guarded/x")).startsWith("fallback: "));
            // an observer of the exchanges
            RouteMetrics metrics = server.getApplicationContext().getBean(RouteMetrics.class);
            assertEquals(1, metrics.count("audited", GatewayObserver.Outcome.RESPONSE, 200));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "GatewayDocsTest")
    static class Upstream implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.POST("/express/{+path}", (request, pathVariables) -> HttpResponse.ok("express").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.POST("/standard/{+path}", (request, pathVariables) -> HttpResponse.ok("standard").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/echo", (request, pathVariables) -> HttpResponse.ok(
                "tenant=" + request.getHeaders().get("X-Tenant", String.class, "-")
                    + " team=" + request.getHeaders().get("X-Audit-Team", String.class, "-")).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
