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

@Property(name = "micronaut.server.error-response-include-message", value = "always")
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
        // the cache is bound by the characters of HTML it holds, not by the number of pages
        int maxChars = 512 * 1024;
        exchangeCustomException(client, 0);
        int pageChars = defaultHtmlErrorResponseBodyProvider.getCache().values().stream()
            .mapToInt(String::length)
            .max()
            .orElseThrow();
        int size = maxChars / pageChars + 10;
        for (int i = 1; i <= size; i++) {
            exchangeCustomException(client, i);
        }
        long cachedChars = defaultHtmlErrorResponseBodyProvider.getCache().values().stream()
            .mapToLong(String::length)
            .sum();
        assertTrue(cachedChars <= maxChars, "the HTML error page cache holds " + cachedChars + " chars");
        assertTrue(defaultHtmlErrorResponseBodyProvider.getCache().size() < size);
    }

    private static void exchangeCustomException(BlockingHttpClient client, int nonce) {
        URI uri = UriBuilder.of("/errorcache").path("/ex").queryParam("nonce", String.valueOf(nonce)).build();
        HttpRequest<?> request = HttpRequest.POST(uri, Collections.emptyMap())
            .accept(MediaType.TEXT_HTML);
        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class, () -> client.exchange(request));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
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
