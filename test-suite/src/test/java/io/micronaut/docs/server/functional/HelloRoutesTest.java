package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HelloRoutesTest {

    @Test
    void theHandlerAnswers() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "HelloRoutesTest"));
             HttpClient client = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            assertEquals("Hello World", client.toBlocking().retrieve(HttpRequest.GET("/hello/World")));
        }
    }
}
