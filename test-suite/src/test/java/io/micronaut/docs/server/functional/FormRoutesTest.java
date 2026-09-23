package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormRoutesTest {

    @TempDir
    static Path uploads;

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "FormRoutesTest",
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
    void aUrlEncodedForm() {
        String answer = client.toBlocking().retrieve(HttpRequest.POST("/forms/signup", Map.of("name", "Ada", "age", "36"))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
        assertEquals("Welcome Ada, 36", answer);
        String withDefault = client.toBlocking().retrieve(HttpRequest.POST("/forms/signup", Map.of("name", "Bob"))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE));
        assertEquals("Welcome Bob, 18", withDefault);
    }

    @Test
    void aCollectedMultipartForm() {
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Ada")
            .addPart("avatar", "ada.png", MediaType.IMAGE_PNG_TYPE, new byte[2048])
            .build();
        String answer = client.toBlocking().retrieve(HttpRequest.POST("/forms/profile", body)
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE));
        assertEquals("Ada sent ada.png, 2048 bytes", answer);
    }

    @Test
    void aStreamedFilePart() throws IOException {
        String content = "line\n".repeat(10_000);
        MultipartBody body = MultipartBody.builder()
            .addPart("file", "lines.txt", MediaType.TEXT_PLAIN_TYPE, content.getBytes(StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.POST("/forms/upload", body)
            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String.class);
        assertEquals(HttpStatus.CREATED, response.getStatus());
        assertEquals(content, Files.readString(uploads.resolve(response.body())));
    }
}
