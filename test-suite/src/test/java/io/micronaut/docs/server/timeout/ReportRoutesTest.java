package io.micronaut.docs.server.timeout;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportRoutesTest {

    @Test
    void theGroupTimeoutApplies() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "ReportRoutesTest"));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            assertEquals("summary", client.toBlocking().retrieve(HttpRequest.GET("/fn-reports/summary")));
        }
    }
}
