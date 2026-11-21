package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrafficConditionRoutesTest {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "TrafficConditionRoutesTest",
            "micronaut.server.host-resolution.host-header", "X-Forwarded-Host"));
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stop() {
        if (client != null) {
            client.stop();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void theHostCookieAndTimeSelectTheRoute() {
        BlockingHttpClient http = client.toBlocking();
        assertEquals("catalog", http.retrieve(HttpRequest.GET("/catalog")));
        assertEquals("partner catalog", http.retrieve(HttpRequest.GET("/catalog").header("X-Forwarded-Host", "acme.partners.example.com")));
        assertEquals("new catalog", http.retrieve(HttpRequest.GET("/catalog").cookie(Cookie.of("catalog", "v2"))));
    }

    @Test
    void theClientAddressSelectsTheRoute() {
        assertEquals("internal", client.toBlocking().retrieve(HttpRequest.GET("/internal/status")));
    }
}
