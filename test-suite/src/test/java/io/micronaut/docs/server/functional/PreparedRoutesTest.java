package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreparedRoutesTest {

    @Test
    void aFunctionAnswersWithAPreparedResponse() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "PreparedRoutesTest"));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient http = client.toBlocking();
            HttpResponse<String> current = http.exchange(HttpRequest.GET("/release"), String.class);
            assertEquals(HttpStatus.OK, current.getStatus());
            assertEquals("release 2026.10", current.body());
            assertEquals("\"2026.10\"", current.getHeaders().get(HttpHeaders.ETAG));

            HttpResponse<String> notModified = http.exchange(HttpRequest.GET("/release").header(HttpHeaders.IF_NONE_MATCH, "\"2026.10\""), String.class);
            assertEquals(HttpStatus.NOT_MODIFIED, notModified.getStatus());
            assertEquals("\"2026.10\"", notModified.getHeaders().get(HttpHeaders.ETAG));
        }
    }
}
