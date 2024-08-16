package io.micronaut.http

import io.micronaut.context.annotation.Property

@Property(name = 'micronaut.server.http-version', value = '2.0')
@Property(name = 'micronaut.server.ssl.enabled', value = 'true')
@Property(name = 'micronaut.server.ssl.build-self-signed', value = 'true')
@Property(name = 'micronaut.server.ssl.port', value = '0')
@Property(name = 'micronaut.http.client.ssl.insecure-trust-all-certificates', value = 'true')
@Property(name = "micronaut.server.netty.log-level", value = 'trace')
@Property(name = "micronaut.http.client.log-level", value = 'trace')
@Property(name = 'micronaut.server.netty.access-logger.enabled', value = 'true')
class Http2AccessLoggerSpec extends HttpAccessLoggerSpec {
}
