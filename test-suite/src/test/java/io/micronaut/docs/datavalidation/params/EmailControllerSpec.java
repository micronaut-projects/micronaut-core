package io.micronaut.docs.datavalidation.params;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class EmailControllerSpec {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeClass
    public static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class, Collections.singletonMap("spec.name", "datavalidationparams"));
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


    //tag::paramsvalidated[]
    @Test
    public void testParametersAreValidated() {
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().exchange("/email/send?subject=Hi&recipient="));
        HttpResponse response = e.getResponse();

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());

        response = client.toBlocking().exchange("/email/send?subject=Hi&recipient=me@micronaut.example");

        assertEquals(HttpStatus.OK, response.getStatus());
    }
    //end::paramsvalidated[]
}
