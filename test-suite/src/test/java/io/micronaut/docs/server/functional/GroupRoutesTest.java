package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroupRoutesTest {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "GroupRoutesTest"));
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
    void theGroupFiltersApplyToEveryRouteOfTheGroup() {
        BlockingHttpClient http = client.toBlocking();
        HttpResponse<String> orders = http.exchange(HttpRequest.GET("/api/orders").header("X-Tenant", "acme"), String.class);
        assertEquals("orders of acme", orders.body());
        assertEquals("v1", orders.getHeaders().get("X-Api"));
        assertEquals("api", orders.getHeaders().get("X-Served-By"));

        HttpClientResponseException noTenant = assertThrows(HttpClientResponseException.class,
            () -> http.exchange(HttpRequest.GET("/api/orders"), String.class));
        assertEquals(HttpStatus.BAD_REQUEST, noTenant.getStatus());
        assertEquals("v1", noTenant.getResponse().getHeaders().get("X-Api"));
    }

    @Test
    void aRouteWithoutAPathIsAtThePrefixOfTheGroup() {
        assertEquals("api of acme", client.toBlocking().retrieve(HttpRequest.GET("/api").header("X-Tenant", "acme")));
    }

    @Test
    void aNestedGroupAddsItsFilters() {
        BlockingHttpClient http = client.toBlocking();
        HttpClientResponseException forbidden = assertThrows(HttpClientResponseException.class,
            () -> http.exchange(HttpRequest.GET("/api/admin/users").header("X-Tenant", "acme"), String.class));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatus());
        assertEquals("users", http.retrieve(HttpRequest.GET("/api/admin/users").header("X-Tenant", "acme").header("X-Role", "admin")));
    }

    @Test
    void routeFiltersChangeTheRequestAndReplaceTheResponse() {
        BlockingHttpClient http = client.toBlocking();
        assertEquals("report 7 as summary", http.retrieve(HttpRequest.GET("/api/reports/7").header("X-Tenant", "acme")));
        HttpClientResponseException gone = assertThrows(HttpClientResponseException.class,
            () -> http.exchange(HttpRequest.GET("/api/reports/7").header("X-Tenant", "acme").header("X-Legacy", "true"), String.class));
        assertEquals(HttpStatus.GONE, gone.getStatus());
    }

    @Test
    void serverFiltersFilterEveryRequestOfTheirPatterns() {
        BlockingHttpClient http = client.toBlocking();
        HttpClientResponseException notFound = assertThrows(HttpClientResponseException.class,
            () -> http.exchange(HttpRequest.GET("/api/missing").header("X-Tenant", "acme"), String.class));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());
        assertEquals("api", notFound.getResponse().getHeaders().get("X-Served-By"));
        assertNull(notFound.getResponse().getHeaders().get("X-Api"));

        assertEquals("orders of acme", http.retrieve(HttpRequest.GET("/v1/orders").header("X-Tenant", "acme")));
    }

    @Test
    void theRoutesOfAGroupHaveItsMediaTypesAndItsExecutor() {
        BlockingHttpClient http = client.toBlocking();
        HttpResponse<String> saved = http.exchange(HttpRequest.POST("/notes", "hello").contentType(MediaType.TEXT_PLAIN_TYPE), String.class);
        assertEquals("saved hello", saved.body());
        assertEquals(MediaType.TEXT_PLAIN, saved.getContentType().map(MediaType::getName).orElse(null));
        HttpClientResponseException unsupported = assertThrows(HttpClientResponseException.class,
            () -> http.exchange(HttpRequest.POST("/notes", "{}").contentType(MediaType.APPLICATION_JSON_TYPE), String.class));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, unsupported.getStatus());

        assertEquals("saved pen", http.retrieve(HttpRequest.POST("/notes/items", new Item(1, "pen"))
            .contentType(MediaType.APPLICATION_JSON_TYPE)));
        assertEquals("1", http.retrieve(HttpRequest.GET("/notes/count")));

        HttpResponse<String> drafts = http.exchange(HttpRequest.GET("/notes/drafts"), String.class);
        assertEquals(MediaType.APPLICATION_JSON, drafts.getContentType().map(MediaType::getName).orElse(null));
        assertEquals("[{\"id\":1,\"name\":\"draft\"}]", drafts.body());
        HttpClientResponseException notAcceptable = assertThrows(HttpClientResponseException.class,
            () -> http.exchange(HttpRequest.GET("/notes/drafts").accept(MediaType.TEXT_PLAIN_TYPE), String.class));
        assertEquals(HttpStatus.NOT_ACCEPTABLE, notAcceptable.getStatus());
    }

    @Test
    void aFilterRunsOnItsExecutorAndTheDeclarationContinuesWithAnd() {
        HttpResponse<String> audited = client.toBlocking().exchange(HttpRequest.GET("/audit/1"), String.class);
        assertEquals("true", audited.header("X-Audited"));
        String body = audited.body();
        assertNotEquals("audited on none", body);
        assertFalse(body.contains("EventLoop"), body);
    }
}
