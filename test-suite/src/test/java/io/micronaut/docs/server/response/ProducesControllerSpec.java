package io.micronaut.docs.server.response;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class ProducesControllerSpec {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeClass
    public static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class, Collections.singletonMap("spec.name", "producesspec"));
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
    public void testContentTypes() {
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/produces"), String.class);

        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getContentType().get());

        response = client.toBlocking().exchange(HttpRequest.GET("/produces/html"), String.class);

        assertEquals(MediaType.TEXT_HTML_TYPE, response.getContentType().get());

        response = client.toBlocking().exchange(HttpRequest.GET("/produces/xml"), String.class);

        assertEquals(MediaType.TEXT_XML_TYPE, response.getContentType().get());
    }
}
