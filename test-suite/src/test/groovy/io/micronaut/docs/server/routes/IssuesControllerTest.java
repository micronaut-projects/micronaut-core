package io.micronaut.docs.server.routes;

// tag::imports[]
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.HttpClient;
import org.junit.*;
import io.micronaut.runtime.server.EmbeddedServer;
import static org.junit.Assert.*;
// end::imports[]

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class IssuesControllerTest {

    // tag::setup[]
    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeClass // <1>
    public static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class);
        client = server
                    .getApplicationContext()
                    .createBean(HttpClient.class, server.getURL());
    }

    @AfterClass // <1>
    public static void stopServer() {
        if(server != null) {
            server.stop();
        }
        if(client != null) {
            client.stop();
        }
    }
    // end::setup[]

    // tag::test[]
    @Test
    public void testIssue() throws Exception {
        String body = client.toBlocking().retrieve("/issues/12"); // <2>
        assertNotNull(body);
        assertEquals( // <3>
                "Issue # 12!",
                body
        );
    }
    // end::test[]
}
