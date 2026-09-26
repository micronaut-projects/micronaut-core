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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditedRoutesTest {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "AuditedRoutesTest",
            "micronaut.router.versioning.enabled", "true",
            "micronaut.router.versioning.header.enabled", "true"));
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
    void theAnnotationsOfARouteBindAFilter() {
        BlockingHttpClient http = client.toBlocking();
        HttpResponse<String> payment = http.exchange(HttpRequest.POST("/payments/10", ""), String.class);
        assertEquals("paid 10", payment.body());
        assertEquals("true", payment.getHeaders().get("X-Audited"));

        HttpResponse<String> refund = http.exchange(HttpRequest.POST("/refunds/5", ""), String.class);
        assertEquals("refunded 5", refund.body());
        assertEquals("true", refund.getHeaders().get("X-Audited"));

        HttpResponse<String> prices = http.exchange(HttpRequest.GET("/prices"), String.class);
        assertEquals("prices", prices.body());
        assertNull(prices.getHeaders().get("X-Audited"));
    }

    @Test
    void theVersionAnnotationOfARouteSelectsIt() {
        BlockingHttpClient http = client.toBlocking();
        HttpResponse<String> v1 = http.exchange(HttpRequest.GET("/receipts/7").header("X-API-VERSION", "1"), String.class);
        assertEquals("receipt v1 7", v1.body());
        assertNull(v1.getHeaders().get("X-Audited"));

        HttpResponse<String> v2 = http.exchange(HttpRequest.GET("/receipts/7").header("X-API-VERSION", "2"), String.class);
        assertEquals("receipt v2 7", v2.body());
        assertEquals("true", v2.getHeaders().get("X-Audited"));
    }

    @Test
    void theRoutesOfAGroupHaveItsExecutorAndAnnotations() {
        BlockingHttpClient http = client.toBlocking();
        HttpResponse<String> users = http.exchange(HttpRequest.GET("/admin/users").header("X-API-VERSION", "2"), String.class);
        // the blocking executor, not the event loop
        assertFalse(users.body().contains("EventLoop"), users.body());
        assertEquals("true", users.getHeaders().get("X-Audited"));

        HttpResponse<String> deleted = http.exchange(HttpRequest.DELETE("/admin/users/3").header("X-API-VERSION", "2"), String.class);
        assertEquals("deleted 3", deleted.body());
        assertEquals("true", deleted.getHeaders().get("X-Audited"));

        // the routes of the group answer the version 2 only
        HttpClientResponseException v1 = assertThrows(HttpClientResponseException.class,
            () -> http.exchange(HttpRequest.GET("/admin/users").header("X-API-VERSION", "1"), String.class));
        assertEquals(HttpStatus.NOT_FOUND, v1.getStatus());
    }

    @Test
    void aDeclaredRoute() {
        assertEquals("balance of main", client.toBlocking().retrieve(HttpRequest.GET("/balance/main")));
    }
}
