package io.micronaut.docs.http.server.secondary;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "secondary.enabled", value = StringUtils.TRUE)
@Property(name = "micronaut.http.client.ssl.insecure-trust-all-certificates", value = StringUtils.TRUE)
public class SecondaryServerTest {
    // tag::inject[]
    @Client(path = "/", id = SecondaryNettyServer.SERVER_ID)
    @Inject
    HttpClient httpClient; // <1>

    @Named(SecondaryNettyServer.SERVER_ID)
    EmbeddedServer embeddedServer; // <2>
    // end::inject[]

    @Test
    void testCallSecondaryServer() {
        final String result = httpClient.toBlocking().retrieve("/test/secondary/server");
        Assertions.assertTrue(result.endsWith(String.valueOf(embeddedServer.getPort())));
        Assertions.assertTrue(embeddedServer.getScheme().equalsIgnoreCase("https"));
    }

    @Controller("/test/secondary/server")
    static class TestController {
        @Get
        String hello(HttpRequest<?> request) {
            return "Hello from: " + request.getServerAddress().getPort();
        }
    }
}
