package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantRouteSourceTest {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "TenantRouteSourceTest"));
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
    void publishedRoutesAnswerUntilTheyAreReplaced() {
        BlockingHttpClient http = client.toBlocking();
        TenantRouteSource source = server.getApplicationContext().getBean(TenantRouteSource.class);

        assertEquals(HttpStatus.NOT_FOUND, notFound(http, "/tenants/acme/home"));

        source.publish(List.of("acme", "globex"));
        HttpResponse<String> acme = http.exchange(HttpRequest.GET("/tenants/acme/home"), String.class);
        assertEquals("home of acme", acme.body());
        assertEquals("acme", acme.getHeaders().get("X-Tenant"));
        assertEquals("home of globex", http.retrieve(HttpRequest.GET("/tenants/globex/home")));

        source.publish(List.of("globex"));
        assertEquals(HttpStatus.NOT_FOUND, notFound(http, "/tenants/acme/home"));
        assertEquals("home of globex", http.retrieve(HttpRequest.GET("/tenants/globex/home")));
    }

    private static HttpStatus notFound(BlockingHttpClient http, String uri) {
        return assertThrows(HttpClientResponseException.class, () -> http.retrieve(HttpRequest.GET(uri))).getStatus();
    }
}
