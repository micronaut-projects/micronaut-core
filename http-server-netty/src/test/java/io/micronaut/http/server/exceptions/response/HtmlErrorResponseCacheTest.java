package io.micronaut.http.server.exceptions.response;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.*;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Property(name = "spec.name", value = "HtmlErrorResponseCacheTest")
@MicronautTest
class HtmlErrorResponseCacheTest {

    @Test
    void cache(@Client("/") HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();

        URI uri = UriBuilder.of("/example").build();
        HttpRequest<?> request = HttpRequest.GET(uri).accept(MediaType.TEXT_HTML);
        Argument<String> arg = Argument.of(String.class);
        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class, () -> client.exchange(request, arg, arg));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        Optional<String> htmlOptional = ex.getResponse().getBody(String.class);
        assertTrue(htmlOptional.isPresent());
        String html = htmlOptional.get();
        assertTrue(html.contains("<!doctype html>"));
        assertTrue(html.contains("Required argument [String a] not specified"));
        assertFalse(html.contains("Required argument [String b] not specified"));

        ex = assertThrows(HttpClientResponseException.class, () -> client.exchange(
                HttpRequest.GET(UriBuilder.of("/example").queryParam("a", "foo").build())
                        .accept(MediaType.TEXT_HTML),
                arg,
                arg));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        htmlOptional = ex.getResponse().getBody(String.class);
        assertTrue(htmlOptional.isPresent());
        html = htmlOptional.get();
        assertTrue(html.contains("<!doctype html>"));
        assertFalse(html.contains("Required argument [String a] not specified"));
        assertTrue(html.contains("Required argument [String b] not specified"));

        HttpResponse<?> response = assertDoesNotThrow(() -> client.exchange(
                HttpRequest.GET(UriBuilder.of("/example")
                        .queryParam("a", "foo")
                        .queryParam("b", "bar")
                        .build())
                        .accept(MediaType.TEXT_HTML),
                arg,
                arg));
        assertEquals(HttpStatus.OK, response.getStatus());
        htmlOptional = response.getBody(String.class);
        assertTrue(htmlOptional.isPresent());
    }

    @Requires(property = "spec.name", value = "HtmlErrorResponseCacheTest")
    @Controller("/example")
    static class ExampleController {
        @Produces({ MediaType.TEXT_HTML})
        @Get
        public String index(String a, String b) {
            return """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <title>Hello World</title>
                    </head>
                    <body>
                    </body>
                    </html>
                    """;
        }
    }
}
