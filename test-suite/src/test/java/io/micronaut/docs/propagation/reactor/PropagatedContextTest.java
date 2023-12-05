package io.micronaut.docs.propagation.reactor;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "spec.name", value = "PropagatedContextSpec")
@MicronautTest
class PropagatedContextTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void testMonoRequest() {
        String hello = client.toBlocking().retrieve(HttpRequest.GET("/hello"), Argument.of(String.class));
        assertEquals("Hello, World", hello);
    }

}
