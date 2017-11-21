package example;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.particleframework.context.ApplicationContext;
import org.particleframework.runtime.server.EmbeddedServer;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.*;

/**
 * Created by graemerocher on 21/11/2017.
 */
public class BookControllerTest {

    @Test
    public void testIndex() throws Exception {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class);

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(new URL(server.getURL(), "/book"))
                .build();
        Response response = client.newCall(
                request
        ).execute();

        ResponseBody body = response.body();
        assertNotNull(body);
        assertEquals(
                body.string(),
                "[{\"title\":\"The Stand\"},{\"title\":\"The Shining\"}]"
        );

        server.stop();
    }
}
