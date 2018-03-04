package example.mail;

import io.reactivex.Flowable;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.particleframework.context.ApplicationContext;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.client.HttpClient;
import org.particleframework.runtime.server.EmbeddedServer;

import java.util.Collections;

import static org.junit.Assert.*;

public class MailControllerTest {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeClass
    public static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class, Collections.singletonMap(
                "consul.client.enabled", false
        ));
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
    @Ignore
    public void testMailSend() throws Exception {
        //String requestBody = "{\"cc\": [\"sergio.delamo@softamo.com\"],\"recipient\": \"sergio.delamo@softamo.com\", \"subject\": \"Interested in Pet\", \"replyTo\": \"sergio.delamo@softamo.com\", \"htmlBody\": \"Body html\", \"bcc\": [\"sergio.delamo@softamo.com\"]}";
        String requestBody = "{\"recipient\": \"sergio.delamo@softamo.com\"}";
        HttpResponse rsp = client.toBlocking().exchange(HttpRequest.POST("/v1/mail/send",requestBody));
        assertEquals(200, rsp.getStatus().getCode());
    }
}