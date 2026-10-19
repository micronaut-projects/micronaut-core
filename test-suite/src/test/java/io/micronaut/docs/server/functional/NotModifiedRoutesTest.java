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

class NotModifiedRoutesTest {

    @Test
    void aRequestWithTheCurrentVersionIsAnsweredWith304() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "NotModifiedRoutesTest"));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            BlockingHttpClient http = client.toBlocking();
            HttpResponse<String> current = http.exchange(HttpRequest.GET("/release"), String.class);
            assertEquals(HttpStatus.OK, current.getStatus());
            assertEquals("release 2026.10", current.body());
            assertEquals("\"2026.10\"", current.getHeaders().get(HttpHeaders.ETAG));

            // one of the listed tags, weak or strong
            for (String ifNoneMatch : new String[]{"\"2026.10\"", "W/\"2026.10\"", "\"2026.09\", \"2026.10\"", "*"}) {
                HttpResponse<String> notModified = http.exchange(HttpRequest.GET("/release").header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch), String.class);
                assertEquals(HttpStatus.NOT_MODIFIED, notModified.getStatus(), ifNoneMatch);
                assertEquals("\"2026.10\"", notModified.getHeaders().get(HttpHeaders.ETAG));
            }
            HttpResponse<String> older = http.exchange(HttpRequest.GET("/release").header(HttpHeaders.IF_NONE_MATCH, "\"2026.09\""), String.class);
            assertEquals(HttpStatus.OK, older.getStatus());
        }
    }
}
