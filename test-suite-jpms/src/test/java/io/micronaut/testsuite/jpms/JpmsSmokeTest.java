package io.micronaut.testsuite.jpms;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.server.netty.NettyEmbeddedServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpmsSmokeTest {

    @Test
    void startsApplicationContextAndDoesJsonRoundtrip() {
        try (ApplicationContext context = ApplicationContext.run()) {
            assertTrue(context.isRunning());
        }
    }

    @Test
    void startsEmbeddedServerAndCallsViaHttpClient() {
        try (ApplicationContext context = ApplicationContext.run();
             NettyEmbeddedServer server = context.getBean(NettyEmbeddedServer.class).start();
             HttpClient client = context.createBean(HttpClient.class, server.getURL())) {

            HttpResponse<String> response = client.toBlocking()
                .exchange(HttpRequest.GET("/hello/world"), String.class);

            assertEquals("Hello World", response.body());
        }
    }
}
