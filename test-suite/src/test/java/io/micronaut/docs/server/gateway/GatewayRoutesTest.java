package io.micronaut.docs.server.gateway;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GatewayRoutesTest {

    @Test
    void theGatewayRoutesRelayToTheUpstream() {
        int port = SocketUtils.findAvailableTcpPort();
        // the application is its own upstream: /orders/{id} below
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
                "spec.name", "GatewayRoutesTest",
                "micronaut.server.port", port,
                "orders.url", "http://localhost:" + port));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            assertEquals("order 7 mn", client.toBlocking().retrieve(HttpRequest.GET("/api/orders/7").header("X-Tenant", "t")));
            // the condition of the route: no X-Tenant, no route
            assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(HttpRequest.GET("/api/orders/7")));
            assertEquals("order 8 -", client.toBlocking().retrieve(HttpRequest.GET("/internal/orders/8")));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "GatewayRoutesTest")
    static class Upstream implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/orders/{id}", (request, pathVariables) -> HttpResponse.ok("order " + pathVariables.getString("id") + " "
                + request.getHeaders().get("X-Gateway", String.class, "-")).contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
