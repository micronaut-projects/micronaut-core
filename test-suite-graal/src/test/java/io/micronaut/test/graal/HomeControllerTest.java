package io.micronaut.test.graal;

import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
class HomeControllerTest {

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Test
    void helloWorld() {
        // TODO: Fix serde
//        assertEquals("Hello World",
//            httpClient.toBlocking().retrieve("/", Map.class).get("message"));
    }
}
