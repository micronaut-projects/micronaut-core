package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocatorRoutesTest {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "LocatorRoutesTest"));
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
    void theRoutesOfTheLocatedTargetAnswer() {
        BlockingHttpClient http = client.toBlocking();
        for (String prefix : List.of("/shops", "/remote-shops")) {
            assertEquals("north", http.retrieve(HttpRequest.GET(prefix + "/north")));
            assertEquals("coffee", http.retrieve(HttpRequest.GET(prefix + "/north/items/1")));
            assertEquals("juice", http.retrieve(HttpRequest.GET(prefix + "/south/items/0")));
            HttpClientResponseException unknownShop = assertThrows(HttpClientResponseException.class,
                () -> http.retrieve(HttpRequest.GET(prefix + "/west/items/0")));
            assertEquals(HttpStatus.NOT_FOUND, unknownShop.getStatus());
            HttpClientResponseException notAllowed = assertThrows(HttpClientResponseException.class,
                () -> http.retrieve(HttpRequest.DELETE(prefix + "/north/items/0")));
            assertEquals(HttpStatus.METHOD_NOT_ALLOWED, notAllowed.getStatus());
        }
        assertEquals("tea", http.retrieve(HttpRequest.GET("/typed-shops/north/items/0")));
    }
}
