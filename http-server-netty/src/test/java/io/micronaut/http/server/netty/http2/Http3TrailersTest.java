package io.micronaut.http.server.netty.http2;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

/**
 * {@link Http2TrailersTest} over HTTP/3, where the trailers are the headers frame that ends the
 * QUIC stream.
 */
@MicronautTest
@Property(name = "spec.name", value = "Http2TrailersTest")
@Property(name = "micronaut.server.ssl.enabled", value = "true")
@Property(name = "micronaut.server.ssl.build-self-signed", value = "true")
@Property(name = "micronaut.server.netty.listeners.a.family", value = "QUIC")
@Property(name = "micronaut.server.netty.listeners.a.port", value = "-1")
@Property(name = "micronaut.http.client.alpn-modes", value = "h3")
@Property(name = "micronaut.http.client.ssl.insecure-trust-all-certificates", value = "true")
public class Http3TrailersTest extends Http2TrailersTest {
}
