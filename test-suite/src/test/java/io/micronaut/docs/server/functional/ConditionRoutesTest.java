package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConditionRoutesTest {

    private static EmbeddedServer server;
    private static HttpClient client;
    private static int managementPort;

    @BeforeAll
    static void start() {
        managementPort = SocketUtils.findAvailableTcpPort();
        server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "ConditionRoutesTest",
            "management.port", managementPort));
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
    void aConditionSelectsTheRoute() {
        BlockingHttpClient http = client.toBlocking();
        assertEquals("search", http.retrieve(HttpRequest.GET("/search")));
        assertEquals("beta search", http.retrieve(HttpRequest.GET("/search").header("X-Beta", "on")));
        assertEquals("beta search", http.retrieve(HttpRequest.GET("/search?beta=true")));
    }

    @Test
    void aFilterReadsTheAttributesOfTheRoute() {
        BlockingHttpClient http = client.toBlocking();
        assertEquals("daily report", http.retrieve(HttpRequest.GET("/reports/daily").header("X-Role", "auditor")));
        HttpClientResponseException forbidden = assertThrows(HttpClientResponseException.class,
            () -> http.retrieve(HttpRequest.GET("/reports/salaries").header("X-Role", "auditor")));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatus());
        assertEquals("salaries", http.retrieve(HttpRequest.GET("/reports/salaries").header("X-Role", "admin")));
    }

    @Test
    void aRouteOnAnotherPort() throws Exception {
        HttpClientResponseException notFound = assertThrows(HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.GET("/management/health")));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());
        try (HttpClient management = HttpClient.create(URI.create("http://" + server.getHost() + ":" + managementPort).toURL())) {
            assertEquals("UP", management.toBlocking().retrieve(HttpRequest.GET("/management/health")));
        }
    }
}
