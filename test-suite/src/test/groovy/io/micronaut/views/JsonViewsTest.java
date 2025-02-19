package io.micronaut.views;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Property(name = "jackson.json-view.enabled", value = "true")
@MicronautTest
class JsonViewsTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void testJsonViewPojo() {
        HttpResponse<String> response = client.toBlocking().exchange("/views/pojo", String.class);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertFalse(response.body().contains("password"));
    }

    @Test
    void testJsonViewList() {
        HttpResponse<String> response = client.toBlocking().exchange("/views/list", String.class);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertFalse(response.body().contains("password"));
    }

    @Test
    void testJsonViewOptional() {
        HttpResponse<String> response = client.toBlocking().exchange("/views/optional", String.class);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertFalse(response.body().contains("password"));
    }

    @Test
    void testJsonViewMono() {
        HttpResponse<String> response = client.toBlocking().exchange("/views/mono", String.class);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertFalse(response.body().contains("password"));
    }

    @Test
    void testJsonViewFlux() {
        HttpResponse<String> response = client.toBlocking().exchange("/views/flux", String.class);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertFalse(response.body().contains("password"));
    }

    @Test
    void testJsonViewFuture() {
        HttpResponse<String> response = client.toBlocking().exchange("/views/future", String.class);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertFalse(response.body().contains("password"));
    }
}
