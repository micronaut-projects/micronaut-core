package io.micronaut.http.client.jdk

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpVersionSelection
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
@Property(name = "spec.name", value = "Http2Spec")
@Property(name = "micronaut.server.http-version", value = "HTTP_2_0")
@Property(name = "micronaut.server.ssl.build-self-signed", value = "true")
@Property(name = "micronaut.server.ssl.enabled", value = "true")
@Property(name = "micronaut.http.client.ssl.insecure-trust-all-certificates", value = "true")
class Http2Spec extends Specification {

    @Inject
    @Client(value = "/", alpnModes = HttpVersionSelection.ALPN_HTTP_2)
    HttpClient client

    def "test http2"() {
        when:
        def response = client.toBlocking().retrieve(HttpRequest.GET("/http2"))

        then:
        response == "hello"
    }

    @Controller("/http2")
    @Requires(property = "spec.name", value = "Http2Spec")
    static class SpecController {

        @Get
        @Produces(MediaType.TEXT_PLAIN)
        String get() {
            "hello"
        }
    }
}
