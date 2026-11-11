package io.micronaut.docs.server.pathvariables;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemControllerTest {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "ItemControllerTest"));
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stopServer() {
        if (client != null) {
            client.stop();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void readsTheVariablesOfTheRoute() {
        assertEquals("Item 5, page 1", get("/items/5"));
        assertEquals("Item 5, page 3", get("/items/5/3"));
    }

    @Test
    void readsTheValuesOfAListVariable() {
        assertEquals("Tags [red, green]", get("/tags/red,green"));
        assertEquals("Sum 6", get("/sum/1,2,3"));
    }

    @Test
    void aValueThatDoesNotConvertIsABadRequest() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> get("/items/abc"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void aFilterReadsTheVariablesOfTheRoute() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> get("/items/7"));
        assertEquals(HttpStatus.GONE, e.getStatus());
    }

    private static String get(String uri) {
        return client.toBlocking().retrieve(HttpRequest.GET(uri));
    }
}
