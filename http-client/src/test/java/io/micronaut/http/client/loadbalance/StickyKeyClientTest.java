package io.micronaut.http.client.loadbalance;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The sticky strategy through an HTTP client: the key is the load balancer key of the request,
 * not the request itself.
 */
class StickyKeyClientTest {

    private static final String SPEC = "StickyKeyClientTest";

    private final List<EmbeddedServer> servers = new ArrayList<>();

    @BeforeEach
    void start() {
        for (int i = 0; i < 3; i++) {
            servers.add(ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC)));
        }
    }

    @AfterEach
    void stop() {
        servers.forEach(EmbeddedServer::close);
    }

    private ApplicationContext client(Map<String, Object> extra) {
        Map<String, Object> properties = new HashMap<>(extra);
        properties.put("spec.name", SPEC);
        properties.put("micronaut.http.services.sticky.urls", servers.stream().map(s -> "http://localhost:" + s.getPort()).toList());
        properties.put("micronaut.http.services.sticky.load-balancer-strategy", "sticky");
        return ApplicationContext.run(properties);
    }

    @Test
    void theKeyAttributePicksTheInstance() {
        try (ApplicationContext ctx = client(Map.of())) {
            HttpClient client = ctx.getBean(HttpClientRegistry.class).getClient(HttpVersion.HTTP_1_1, "sticky", null);
            Map<String, String> byKey = new HashMap<>();
            for (int i = 0; i < 30; i++) {
                String key = "user-" + i;
                byKey.put(key, client.toBlocking().retrieve(LoadBalancerKey.set(HttpRequest.GET("/sticky-key/port"), key)));
            }
            Assertions.assertEquals(3, new HashSet<>(byKey.values()).size(), "Different keys spread across the instances: " + byKey);
            for (Map.Entry<String, String> entry : byKey.entrySet()) {
                for (int i = 0; i < 3; i++) {
                    Assertions.assertEquals(entry.getValue(), client.toBlocking().retrieve(LoadBalancerKey.set(HttpRequest.GET("/sticky-key/port"), entry.getKey())), entry.getKey());
                }
            }
        }
    }

    @Test
    void theKeyHeaderPicksTheInstance() {
        try (ApplicationContext ctx = client(Map.of("micronaut.http.services.sticky.load-balancer-key-header", "X-Session"))) {
            HttpClient client = ctx.getBean(HttpClientRegistry.class).getClient(HttpVersion.HTTP_1_1, "sticky", null);
            Map<String, String> byKey = new HashMap<>();
            for (int i = 0; i < 30; i++) {
                String key = "session-" + i;
                byKey.put(key, client.toBlocking().retrieve(HttpRequest.GET("/sticky-key/port").header("X-Session", key)));
            }
            Assertions.assertEquals(3, new HashSet<>(byKey.values()).size(), "Different keys spread across the instances: " + byKey);
            for (Map.Entry<String, String> entry : byKey.entrySet()) {
                Assertions.assertEquals(entry.getValue(), client.toBlocking().retrieve(HttpRequest.GET("/sticky-key/port").header("X-Session", entry.getKey())), entry.getKey());
            }
        }
    }

    @Test
    void withoutAKeyTheRequestsAreSpread() {
        try (ApplicationContext ctx = client(Map.of())) {
            HttpClient client = ctx.getBean(HttpClientRegistry.class).getClient(HttpVersion.HTTP_1_1, "sticky", null);
            Set<String> ports = new HashSet<>();
            for (int i = 0; i < 6; i++) {
                ports.add(client.toBlocking().retrieve("/sticky-key/port"));
            }
            Assertions.assertEquals(3, ports.size(), "Round robin without a key: " + ports);
        }
    }

    @Controller("/sticky-key")
    @Requires(property = "spec.name", value = SPEC)
    static final class PortController {
        private final EmbeddedServer server;

        PortController(EmbeddedServer server) {
            this.server = server;
        }

        @Get("/port")
        String port() {
            return String.valueOf(server.getPort());
        }
    }
}
