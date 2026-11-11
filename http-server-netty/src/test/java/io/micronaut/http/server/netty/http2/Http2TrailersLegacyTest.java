package io.micronaut.http.server.netty.http2;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;

/**
 * {@link Http2TrailersTest} with the multiplexed stream pipeline, where the trailers arrive as
 * the trailing headers of a {@code LastHttpContent}.
 */
@MicronautTest
@Property(name = "spec.name", value = "Http2TrailersTest")
@Property(name = "micronaut.server.http-version", value = "2.0")
@Property(name = "micronaut.server.ssl.enabled", value = "false")
@Property(name = "micronaut.server.netty.legacy-multiplex-handlers", value = "true")
@Property(name = "micronaut.http.client.plaintext-mode", value = "h2c_prior_knowledge")
public class Http2TrailersLegacyTest extends Http2TrailersTest {
}
