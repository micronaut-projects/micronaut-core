package example;

import org.junit.Test;
import org.particleframework.context.ApplicationContext;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.client.HttpClient;
import org.particleframework.runtime.server.EmbeddedServer;

import static org.junit.Assert.*;

/**
 * Created by graemerocher on 21/11/2017.
 */
public class BookControllerTest {

    @Test
    public void testIndex() throws Exception {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class);

        HttpClient httpClient = server.getApplicationContext()
                                      .createBean(HttpClient.class, server.getURL());

        String body = httpClient.toBlocking().retrieve(HttpRequest.GET("/book"));
        assertNotNull(body);
        assertEquals(
                body,
                "[{\"title\":\"The Stand\"},{\"title\":\"The Shining\"}]"
        );

        server.stop();
    }
}
