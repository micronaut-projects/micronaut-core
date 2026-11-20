package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * A client filter of the Netty raw client adds a cookie to the raw request.
 */
class RawClientFilterAddsCookieSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'RawClientFilterAddsCookieSpec'])

    @Shared
    @AutoCleanup
    RawHttpClient client = server.applicationContext.createBean(RawHttpClient, server.URI)

    void "a client filter adds a cookie to a raw request"() {
        when:
        def response = Mono.from(client.exchange(
                HttpRequest.GET(server.URI.toString() + "/raw-cookie-filter/cookies").cookie(Cookie.of("sent", "by-client")),
                (CloseableByteBody) null,
                (Thread) null
        )).cast(ByteBodyHttpResponse).block()

        then:
        response.status().code == 200
        response.byteBody().buffer().get().toString(StandardCharsets.UTF_8) == "by-client by-filter"

        cleanup:
        response?.close()
    }

    @Controller("/raw-cookie-filter")
    @Requires(property = "spec.name", value = "RawClientFilterAddsCookieSpec")
    static class UpstreamController {
        @Get(value = "/cookies", produces = MediaType.TEXT_PLAIN)
        String cookies(@CookieValue("sent") String sent, @CookieValue("added") String added) {
            return sent + " " + added
        }
    }

    @ClientFilter("/raw-cookie-filter/**")
    @Requires(property = "spec.name", value = "RawClientFilterAddsCookieSpec")
    static class AddCookieFilter {
        @RequestFilter
        void addCookie(MutableHttpRequest<?> request) {
            request.cookie(Cookie.of("added", "by-filter"))
        }
    }
}
