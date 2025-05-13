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
@Property(name = "micronaut.server.ssl.enabled", value = "true")
@Property(name = "micronaut.server.ssl.buildSelfSigned", value = "true")
@Property(name = "micronaut.server.ssl.port", value = "-1")
@Property(name = "micronaut.server.http-version", value = "2.0")
@Property(name = "micronaut.http.client.ssl.insecure-trust-all-certificates", value = "true")
@Property(name = "spec.name", value = "HttpRequestEventTest")
public class Http2RequestEventTest extends HttpRequestEventTest {
    @Override
    @EnableEvent("io.micronaut.http.server.netty.handler.Http2RequestEvent")
    @Test
    void test(@Client("/") HttpClient httpClient) {
        super.test(httpClient);

        JfrEventsAssert.assertThat(jfrEvents).contains(ExpectedEvent.event(Http2RequestEvent.class.getName())
            .with("method", "GET")
            .with("uri", "/foo")
            .with("status", 234)
            .with("streamId", 3)
        );
    }
}
