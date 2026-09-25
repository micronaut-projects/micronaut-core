package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpMethod;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemRoutesTest {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "ItemRoutesTest"));
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
    void itemsAreCreatedReadAndDeleted() {
        BlockingHttpClient http = client.toBlocking();
        HttpResponse<Item> created = http.exchange(HttpRequest.POST("/items", new Item(0, "pen")), Item.class);
        assertEquals(HttpStatus.CREATED, created.getStatus());
        long id = created.body().id();

        assertEquals(new Item(id, "pen"), http.retrieve(HttpRequest.GET("/items/" + id), Item.class));
        assertEquals("pen", http.retrieve(HttpRequest.GET("/items/" + id + "/name")));
        assertEquals("touched " + id + " with PATCH", http.retrieve(HttpRequest.PATCH("/items/" + id + "/touch", "")));
        assertEquals("touched " + id + " with PUT", http.retrieve(HttpRequest.PUT("/items/" + id + "/touch", "")));
        assertTrue(http.retrieve(HttpRequest.GET("/items/count"), Integer.class) >= 1);

        assertEquals(HttpStatus.NO_CONTENT, http.exchange(HttpRequest.DELETE("/items/" + id)).getStatus());
        HttpClientResponseException notFound = assertThrows(HttpClientResponseException.class,
            () -> http.retrieve(HttpRequest.GET("/items/" + id)));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus());
    }

    @Test
    void aCustomMethodAndANullableBody() {
        BlockingHttpClient http = client.toBlocking();
        assertEquals("items: PROPFIND", http.retrieve(HttpRequest.create(HttpMethod.CUSTOM, "/items", "PROPFIND")));
        assertEquals(HttpStatus.NO_CONTENT, http.exchange(HttpRequest.POST("/items/optional", null)
            .contentType(MediaType.APPLICATION_JSON_TYPE)).getStatus());
        assertEquals(HttpStatus.CREATED, http.exchange(HttpRequest.POST("/items/optional", new Item(0, "cup"))).getStatus());
    }

    @Test
    void anImplicitHeadRouteAndAMethodNotAllowed() {
        BlockingHttpClient http = client.toBlocking();
        Item item = http.retrieve(HttpRequest.POST("/items", new Item(0, "book")), Item.class);
        assertEquals(HttpStatus.OK, http.exchange(HttpRequest.HEAD("/items/" + item.id())).getStatus());
        HttpClientResponseException notAllowed = assertThrows(HttpClientResponseException.class,
            () -> http.exchange(HttpRequest.PATCH("/items/" + item.id(), "")));
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, notAllowed.getStatus());
    }
}
