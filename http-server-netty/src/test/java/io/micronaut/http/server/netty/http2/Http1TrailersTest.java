package io.micronaut.http.server.netty.http2;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

/**
 * The shared trailer exchange over HTTP/1.1, including a response whose body is aggregated into
 * its terminating content message.
 */
@MicronautTest
@Property(name = "spec.name", value = "Http2TrailersTest")
@Property(name = "micronaut.server.http-version", value = "1.1")
@Property(name = "micronaut.server.ssl.enabled", value = "false")
@Property(name = "micronaut.http.client.plaintext-mode", value = "http_1")
public class Http1TrailersTest extends Http2TrailersTest {
}
