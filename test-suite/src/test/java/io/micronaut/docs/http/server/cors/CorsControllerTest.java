package io.micronaut.docs.http.server.cors;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.*;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorsControllerTest {

    @Test
    void crossOriginWithAllowedOrigin() {

        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class, CollectionUtils.mapOf("spec.name", "CorsControllerSpec"));
        HttpRequest<?> request = preflight(UriBuilder.of("/hello"), "https://myui.com", HttpMethod.GET);
        HttpClient httpClient = embeddedServer.getApplicationContext().createBean(HttpClient.class, embeddedServer.getURL());
        BlockingHttpClient client = httpClient.toBlocking();

        assertDoesNotThrow(() -> client.exchange(request));

        httpClient.close();
        embeddedServer.close();
    }

    @Test
    void crossOriginWithNotAllowedOrigin() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class, CollectionUtils.mapOf("spec.name", "CorsControllerSpec"));
        HttpRequest<?> request = preflight(UriBuilder.of("/hello"), "https://google.com", HttpMethod.GET);
        HttpClient httpClient = embeddedServer.getApplicationContext().createBean(HttpClient.class, embeddedServer.getURL());
        BlockingHttpClient client = httpClient.toBlocking();

        Executable e = () -> client.exchange(request);
        assertThrows(HttpClientResponseException.class, e);

        httpClient.close();
        embeddedServer.close();
    }

    private static MutableHttpRequest<?> preflight(UriBuilder uriBuilder, String originValue, HttpMethod method) {
        return HttpRequest.OPTIONS(uriBuilder.build())
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN)
            .header(HttpHeaders.ORIGIN, originValue)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method);
    }
}
