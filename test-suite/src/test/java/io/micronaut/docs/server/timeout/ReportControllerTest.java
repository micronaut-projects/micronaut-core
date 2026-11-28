package io.micronaut.docs.server.timeout;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportControllerTest {

    @Test
    void theRoutesAnswerWithinTheirTimeout() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "ReportControllerTest"));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            assertEquals("summary", client.toBlocking().retrieve(HttpRequest.GET("/reports/summary")));
            assertEquals("export", client.toBlocking().retrieve(HttpRequest.GET("/reports/export")));
            assertEquals("archive", client.toBlocking().retrieve(HttpRequest.GET("/reports/archive")));
            HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().retrieve(HttpRequest.GET("/reports/rebuild")));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
            e = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().retrieve(HttpRequest.GET("/index/rebuild")));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        }
    }
}
