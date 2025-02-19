package example.micronaut;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@Property(name = "micronaut.server.ssl.enabled", value = "true")
@Property(name = "micronaut.server.ssl.buildSelfSigned", value = "true")
@Property(name = "micronaut.server.ssl.port", value = "0")
@Property(name = "micronaut.http.client.ssl.insecure-trust-all-certificates", value = "true")
public class HelloControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void testHelloWorldResponse() {
        String response = client.toBlocking()
            .retrieve(HttpRequest.GET("/hello"));
        assertEquals("Hello World", response);
    }

}
