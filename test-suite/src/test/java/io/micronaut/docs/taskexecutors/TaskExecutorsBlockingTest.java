package io.micronaut.docs.taskexecutors;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


@Property(name = "spec.name", value = "TaskExecutorsBlockingTest")
@MicronautTest
class TaskExecutorsBlockingTest {
    @Inject
    @Client("/")
    HttpClient httpClient;

    @Test
    void testMethodAnnotatedWithTaskExecutorsBlocking() {
        BlockingHttpClient client = httpClient.toBlocking();
        HttpRequest<?> request = HttpRequest.GET(UriBuilder.of("/hello").path("world").build()).accept(MediaType.TEXT_PLAIN);
        ThrowingSupplier<HttpResponse<String>> supplier = () -> client.exchange(request, String.class);
        HttpResponse<?> response = assertDoesNotThrow(supplier);
        assertEquals(HttpStatus.OK, response.status());
        Optional<String> txt = response.getBody(String.class);
        assertTrue(txt.isPresent());
        assertEquals("Hello World", txt.get());
    }
}
