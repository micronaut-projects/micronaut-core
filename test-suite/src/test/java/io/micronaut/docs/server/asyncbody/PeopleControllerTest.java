package io.micronaut.docs.server.asyncbody;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeopleControllerTest {

    private static final int BUFFER_LIMIT = 256 * 1024;

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "PeopleControllerTest",
            "micronaut.server.max-request-buffer-size", BUFFER_LIMIT
        ));
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
    void decodesTheBody() {
        HttpResponse<Person> response = client.toBlocking().exchange(
            HttpRequest.POST("/people", "{\"name\":\"Fred\",\"age\":45}").contentType(MediaType.APPLICATION_JSON_TYPE),
            Person.class);
        assertEquals(HttpStatus.CREATED, response.getStatus());
        assertEquals(new Person("Fred", 45), response.body());
    }

    @Test
    void aMalformedBodyIsABadRequest() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(
            HttpRequest.POST("/people", "{\"name\":").contentType(MediaType.APPLICATION_JSON_TYPE)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void aBodyLargerThanTheBufferLimitIsTooLarge() {
        String json = "{\"name\":\"" + "x".repeat(4 * BUFFER_LIMIT) + "\",\"age\":45}";
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(
            HttpRequest.POST("/people", json).contentType(MediaType.APPLICATION_JSON_TYPE)));
        assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, e.getStatus());
    }

    @Test
    void importsTheElementsOfAJsonArray() {
        StringJoiner array = new StringJoiner(",", "[", "]");
        for (int i = 0; i < 2000; i++) {
            array.add("{\"name\":\"" + "x".repeat(1000) + i + "\",\"age\":" + i + "}");
        }
        // the limit applies to each element, not to the whole body
        assertTrue(array.length() > 4 * BUFFER_LIMIT);
        String response = client.toBlocking().retrieve(
            HttpRequest.POST("/people/import", array.toString()).contentType(MediaType.APPLICATION_JSON_TYPE));
        assertEquals("Imported 2000", response);
    }

    @Test
    void importsTheElementsOfAJsonStream() {
        String stream = "{\"name\":\"Fred\",\"age\":45}\n{\"name\":\"Wilma\",\"age\":40}\n";
        String response = client.toBlocking().retrieve(
            HttpRequest.POST("/people/import", stream).contentType(MediaType.APPLICATION_JSON_STREAM_TYPE));
        assertEquals("Imported 2", response);
    }

    @Test
    void aFilterReadsACopyAndTheControllerReadsTheBody() {
        String response = client.toBlocking().retrieve(
            HttpRequest.POST("/messages", "Hello Fred").contentType(MediaType.TEXT_PLAIN_TYPE));
        assertEquals("Received Hello Fred", response);
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(
            HttpRequest.POST("/messages", "Buy spam").contentType(MediaType.TEXT_PLAIN_TYPE)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
    }

    @Test
    void readsTheBodyAsText() {
        String response = client.toBlocking().retrieve(
            HttpRequest.POST("/people/notes", "Call Fred").contentType(MediaType.TEXT_PLAIN_TYPE));
        assertEquals("Received 9 characters", response);
    }
}
