package io.micronaut.http.server.netty.handler;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.HttpClient;
import org.junit.jupiter.api.Assertions;
import org.moditect.jfrunit.JfrEvents;

public class HttpRequestEventTest {
    public JfrEvents jfrEvents = new JfrEvents();

    void test(HttpClient httpClient) {
        Assertions.assertEquals("bar", httpClient.toBlocking().retrieve("/foo"));
    }

    @Controller
    @Requires(property = "spec.name", value = "HttpRequestEventTest")
    static final class MyController {
        @Get("/foo")
        public HttpResponse<?> foo() {
            return HttpResponse.status(234, "bla").body("bar");
        }
    }
}
