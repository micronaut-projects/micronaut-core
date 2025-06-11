package io.micronaut.http.server.netty.errors.handler;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class PropagatedContextExceptionHandlerTest {

    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class,
        Map.of("spec.name", "PropagatedContextExceptionHandlerTest")
    );

    HttpClient httpClient = embeddedServer.getApplicationContext().createBean(HttpClient.class, embeddedServer.getURL());

    @Test
    void testNonBlockingPropagatingTrace() {
        HttpResponse<?> response;

        try {
            response = httpClient.toBlocking().exchange(HttpRequest.GET("/non-blocking"), Map.class);
        } catch (HttpClientResponseException e) {
            response = e.getResponse();
        }

        Assertions.assertEquals("1234", response.getBody(Map.class).get().get("controllerTraceId"));
        Assertions.assertEquals("1234", response.getBody(Map.class).get().get("exceptionHandlerTraceId"));
    }

    @Test
    void testBlockingNotPropagatingMDCTrace() {
        HttpResponse<?> response;

        try {
            response = httpClient.toBlocking().exchange(HttpRequest.GET("/blocking"), Map.class);
        } catch (HttpClientResponseException e) {
            response = e.getResponse();
        }

        Assertions.assertEquals("1234", response.getBody(Map.class).get().get("controllerTraceId"));
        Assertions.assertEquals("1234", response.getBody(Map.class).get().get("exceptionHandlerTraceId"));
    }
}
