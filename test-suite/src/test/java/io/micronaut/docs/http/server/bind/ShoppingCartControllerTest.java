package io.micronaut.docs.http.server.bind;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ShoppingCartControllerTest {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeClass
    public static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class);
        client = server
                .getApplicationContext()
                .createBean(HttpClient.class, server.getURL());
    }

    @AfterClass
    public static void stopServer() {
        if(server != null) {
            server.stop();
        }
        if(client != null) {
            client.stop();
        }
    }

    @Test
    public void testBindingBadCredentials() {
        HttpRequest<?> request = HttpRequest.GET("/customBinding/annotated")
                .cookie(Cookie.of("shoppingCart", "{}"));
        HttpClientResponseException responseException = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(request));

        assertEquals("Required ShoppingCart [sessionId] not specified", responseException.getMessage());

    }

    @Test
    public void testAnnotationBinding() {
        HttpRequest<?> request = HttpRequest.GET("/customBinding/annotated")
                .cookie(Cookie.of("shoppingCart", "{\"sessionId\": 5}"));
        String response = client.toBlocking().retrieve(request);

        assertEquals("Session:5", response);
    }

    @Test
    public void testTypeBinding() {
        HttpRequest<?> request = HttpRequest.GET("/customBinding/typed")
                .cookie(Cookie.of("shoppingCart", "{\"sessionId\": 5, \"total\": 20}"));

        Map<String, Object> body = client.toBlocking().retrieve(request, Argument.mapOf(String.class, Object.class));

        assertEquals("5", body.get("sessionId"));
        assertEquals(20, body.get("total"));
    }
}
