package io.micronaut.docs.server.filters;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.docs.server.intro.HelloControllerSpec;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;


public class TraceFilterSpec {
    private static EmbeddedServer server;
    private static RxHttpClient client;

    @BeforeClass
    public static void setupServer() {
        Map<String, Object> map = new HashMap<>();
        map.put("spec.name", HelloControllerSpec.class.getSimpleName());
        map.put("spec.lang", "java");

        server = ApplicationContext.run(EmbeddedServer.class, map, Environment.TEST);
        client = server
                .getApplicationContext()
                .createBean(RxHttpClient.class, server.getURL());
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
    public void testTraceFilter() {
        HttpResponse response = client.toBlocking().exchange(HttpRequest.GET("/hello"));

        assertEquals("true", response.getHeaders().get("X-Trace-Enabled"));
    }
}

