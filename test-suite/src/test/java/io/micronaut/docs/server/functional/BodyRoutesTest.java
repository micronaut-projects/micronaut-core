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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BodyRoutesTest {

    @TempDir
    static Path uploads;

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "BodyRoutesTest",
            "uploads.directory", uploads.toString()));
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
    void aDecodedBody() {
        HttpResponse<Item> response = client.toBlocking().exchange(HttpRequest.POST("/async/items", new Item(0, "lamp")), Item.class);
        assertEquals(HttpStatus.CREATED, response.getStatus());
        assertEquals("lamp", response.body().name());
    }

    @Test
    void theElementsOfAJsonArray() {
        ItemRepository items = server.getApplicationContext().getBean(ItemRepository.class);
        HttpResponse<?> response = client.toBlocking().exchange(HttpRequest.POST("/async/items/import",
            List.of(new Item(101, "a"), new Item(102, "b"), new Item(103, "c"))));
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        assertEquals("b", items.find(102).name());
    }

    @Test
    void boundedText() {
        BlockingHttpClient http = client.toBlocking();
        assertEquals("received 5 characters", http.retrieve(HttpRequest.POST("/async/notes", "hello").contentType(MediaType.TEXT_PLAIN_TYPE)));
        HttpClientResponseException tooLarge = assertThrows(HttpClientResponseException.class, () ->
            http.retrieve(HttpRequest.POST("/async/notes", "x".repeat(2048)).contentType(MediaType.TEXT_PLAIN_TYPE)));
        assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, tooLarge.getStatus());
    }

    @Test
    void aBodyWrittenToAFile() throws IOException {
        byte[] content = new byte[100_000];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) i;
        }
        String name = client.toBlocking().retrieve(HttpRequest.PUT("/async/files", content).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE));
        assertArrayEquals(content, Files.readAllBytes(uploads.resolve(name)));
    }

    @Test
    void aRequestRejectedWithoutReadingTheBody() {
        BlockingHttpClient http = client.toBlocking();
        HttpClientResponseException unauthorized = assertThrows(HttpClientResponseException.class, () ->
            http.retrieve(HttpRequest.POST("/async/guarded", new byte[10]).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE)));
        assertEquals(HttpStatus.UNAUTHORIZED, unauthorized.getStatus());
        assertEquals("accepted 10 bytes", http.retrieve(HttpRequest.POST("/async/guarded", new byte[10])
            .contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE)
            .header("X-Token", "secret")));
    }
}
