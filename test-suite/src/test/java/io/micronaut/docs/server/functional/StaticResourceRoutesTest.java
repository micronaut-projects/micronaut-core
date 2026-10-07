package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StaticResourceRoutesTest {

    @Test
    void theRoutesServeStaticResources() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of(
                "spec.name", "StaticResourceRoutesTest",
                "site.directory", "src/test/resources/functional-static/site"));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient http = client.toBlocking();

            HttpResponse<String> css = http.exchange(HttpRequest.GET("/assets/css/site.css"), String.class);
            assertEquals(HttpStatus.OK, css.getStatus());
            assertEquals("body { color: teal; }\n", css.body());
            assertEquals("text/css", css.getContentType().orElseThrow().getName());
            assertEquals("true", css.getHeaders().get("X-Assets"));
            assertEquals("public, max-age=31536000, immutable", css.getHeaders().get("Cache-Control"));

            HttpResponse<String> hello = http.exchange(HttpRequest.GET("/site/hello.txt"), String.class);
            assertEquals(HttpStatus.OK, hello.getStatus());
            assertEquals("Hello from the file system\n", hello.body());
            assertEquals("text/plain", hello.getContentType().orElseThrow().getName());

            // the index file at the prefix of the group, with the content type of the file, not of the group
            HttpResponse<String> manual = http.exchange(HttpRequest.GET("/manual"), String.class);
            assertEquals(HttpStatus.OK, manual.getStatus());
            assertEquals("<h1>Manual</h1>\n", manual.body());
            assertEquals("text/html", manual.getContentType().orElseThrow().getName());
            assertEquals("true", manual.getHeaders().get("X-Manual"));

            // a missing file, and paths that try to leave the directory, are not found
            for (String path : List.of("/assets/missing.css", "/assets/../secret.txt", "/assets/%2e%2e/secret.txt",
                    "/assets/..%2fsecret.txt", "/site/..%2f..%2fsecret.txt")) {
                var request = HttpRequest.GET(path);
                HttpClientResponseException notFound = assertThrows(HttpClientResponseException.class,
                    () -> http.exchange(request, String.class), path);
                assertEquals(HttpStatus.NOT_FOUND, notFound.getStatus(), path);
            }
        }
    }
}
