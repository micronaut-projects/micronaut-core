package io.micronaut.http.server.exceptions.response;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Property(name = "spec.name", value = "DefaultHtmlErrorResponseBodyProviderCacheMaxSize")
@MicronautTest
class DefaultHtmlErrorResponseBodyProviderCacheMaxSizeTest {

    @Test
    void errorResponseCacheHasAMaxSize(@Client("/") HttpClient httpClient,
                                       DefaultHtmlErrorResponseBodyProvider defaultHtmlErrorResponseBodyProvider) {
        BlockingHttpClient client = httpClient.toBlocking();
        int max = 10;
        for (int i = 1; i <= max; i++) {
            URI uri = UriBuilder.of("/errorcache").queryParam("nonce", String.valueOf(i)).build();
            HttpRequest<?> request = HttpRequest.POST(uri, Collections.emptyMap())
                .accept(MediaType.TEXT_HTML);
            HttpClientResponseException ex = assertThrows(HttpClientResponseException.class, () -> client.exchange(request));
            assertEquals(HttpStatus.NOT_ACCEPTABLE, ex.getStatus());
        }
        assertEquals(1, defaultHtmlErrorResponseBodyProvider.getCache().size());
    }

    @Test
    void errorResponseCacheHasAMaxSizeForCustomException(@Client("/") HttpClient httpClient,
                                       DefaultHtmlErrorResponseBodyProvider defaultHtmlErrorResponseBodyProvider) {
        BlockingHttpClient client = httpClient.toBlocking();
        int size = 110;
        int max = 100;
        assertTrue(max < size);
        for (int i = 1; i <= size; i++) {
            URI uri = UriBuilder.of("/errorcache").path("/ex").queryParam("nonce", String.valueOf(i)).build();
            HttpRequest<?> request = HttpRequest.POST(uri, Collections.emptyMap())
                .accept(MediaType.TEXT_HTML);
            HttpClientResponseException ex = assertThrows(HttpClientResponseException.class, () -> client.exchange(request));
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
        }
        assertEquals(max, defaultHtmlErrorResponseBodyProvider.getCache().size());
    }

    @Requires(property = "spec.name", value = "DefaultHtmlErrorResponseBodyProviderCacheMaxSize")
    @Controller("/errorcache")
    static class OnlyPostController {
        @Produces(MediaType.TEXT_PLAIN)
        @Status(HttpStatus.OK)
        @Post
        void index() {
        }

        @Produces(MediaType.TEXT_HTML)
        @Post("/ex")
        @Status(HttpStatus.OK)
        void ex(HttpRequest<?> request) {
            throw  new CustomException(request);
        }
    }

    static class CustomException extends RuntimeException {
        CustomException(HttpRequest<?> request) {
            super("foobar "+ request.getUri());
        }
    }

}
