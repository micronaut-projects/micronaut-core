package io.micronaut.docs.server.binding;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.runtime.server.EmbeddedServer;

public class MovieTicketControllerSpec {

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
    public void testBindingBean() {
        UriTemplate template = new UriTemplate("/api/movie/ticket/terminator{?minPrice,maxPrice}");
        Map<String, Object> params = new HashMap<>();
        params.put("minPrice", 5.0);
        params.put("maxPrice", 20.0);

        HttpResponse response = client.toBlocking().exchange(template.expand(params));

        assertEquals(HttpStatus.OK, response.status());
    }

}