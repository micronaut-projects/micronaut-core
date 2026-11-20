package io.micronaut.http.server.exceptions.response;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Property(name = "spec.name", value = "HtmlErrorPageCacheWeightTest")
@MicronautTest
class HtmlErrorPageCacheWeightTest {

    @Inject
    DefaultHtmlErrorResponseBodyProvider provider;

    @Inject
    @Client("/")
    HttpClient httpClient;

    @Test
    void largeErrorPagesDoNotStayInTheCache() {
        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class, () ->
            httpClient.toBlocking().exchange(HttpRequest.POST("/html-cache-weight/pages", "pages=" + "'".repeat(300_000))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.TEXT_HTML), String.class));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        long cachedChars = provider.getCache().values().stream()
            .mapToLong(String::length)
            .sum();
        assertTrue(cachedChars < 1_000_000, "the HTML error page cache holds " + cachedChars + " chars after one request");
    }

    @Requires(property = "spec.name", value = "HtmlErrorPageCacheWeightTest")
    @Controller("/html-cache-weight")
    static class PagesController {
        @Produces(MediaType.TEXT_HTML)
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Post("/pages")
        void pages(int pages) {
            throw new UnsupportedOperationException();
        }
    }
}
