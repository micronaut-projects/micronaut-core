package io.micronaut.docs.server.routes;

// tag::imports[]
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
// end::imports[]

// tag::class[]
public class IssuesControllerTest {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeClass // <1>
    public static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class);
        client = server
                    .getApplicationContext()
                    .createBean(HttpClient.class, server.getURL());
    }

    @AfterClass // <2>
    public static void stopServer() {
        if(server != null) {
            server.stop();
        }
        if(client != null) {
            client.stop();
        }
    }

    @Test
    public void testIssue() throws Exception {
        String body = client.toBlocking().retrieve("/issues/12"); // <3>

        assertNotNull(body);
        assertEquals("Issue # 12!", body); // <4>
    }

    @Test
    public void testShowWithInvalidInteger() {
        HttpClientResponseException e =Assertions.assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange("/issues/hello"));

        assertEquals(400, e.getStatus().getCode()); // <5>
    }

    @Test
    public void testIssueWithoutNumber() {
        HttpClientResponseException e = Assertions.assertThrows(HttpClientResponseException.class, () ->
                client.toBlocking().exchange("/issues/"));

        assertEquals(404, e.getStatus().getCode()); // <6>
    }
}
// end::class[]
