package io.micronaut.docs.server.body;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MessageControllerSpec {

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
    public void testEchoResponse() {
        String body = "My Text";
        String response = client.toBlocking().retrieve(
                HttpRequest.POST("/receive/echo", body)
                           .contentType(MediaType.TEXT_PLAIN_TYPE), String.class);

        assertEquals(body, response);
    }

    @Test
    public void testEchoReactiveResponse() {
        String body = "My Text";
        String response = client.toBlocking().retrieve(
                HttpRequest.POST("/receive/echo-flow", body)
                        .contentType(MediaType.TEXT_PLAIN_TYPE), String.class);

        assertEquals(body, response);
    }
}
