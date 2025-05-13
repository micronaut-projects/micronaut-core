package io.micronaut.http.server.netty.handler;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.moditect.jfrunit.EnableEvent;
import org.moditect.jfrunit.ExpectedEvent;
import org.moditect.jfrunit.JfrEventTest;
import org.moditect.jfrunit.JfrEventsAssert;

@JfrEventTest
@MicronautTest
@Property(name = "spec.name", value = "HttpRequestEventTest")
public class Http1RequestEventTest extends HttpRequestEventTest {
    @Override
    @EnableEvent("io.micronaut.http.server.netty.handler.Http1RequestEvent")
    @Test
    void test(@Client("/") HttpClient httpClient) {
        super.test(httpClient);

        JfrEventsAssert.assertThat(jfrEvents).contains(ExpectedEvent.event(Http1RequestEvent.class.getName())
            .with("method", "GET")
            .with("uri", "/foo")
            .with("status", 234)
        );
    }
}
