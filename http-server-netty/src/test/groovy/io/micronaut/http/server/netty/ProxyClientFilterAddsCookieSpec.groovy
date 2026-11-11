package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.ProxyHttpClient
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * A client filter of the Netty proxy client adds a cookie to the proxied server request.
 */
class ProxyClientFilterAddsCookieSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ProxyClientFilterAddsCookieSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "a client filter adds a cookie to a proxied server request"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.GET("/proxy-cookie-filter/relay").cookie(Cookie.of("sent", "by-client"))) == "by-client by-filter"
    }

    @Controller("/proxy-cookie-filter")
    @Requires(property = "spec.name", value = "ProxyClientFilterAddsCookieSpec")
    static class RelayController {
        private final ProxyHttpClient proxyHttpClient
        private final EmbeddedServer embeddedServer

        RelayController(ProxyHttpClient proxyHttpClient, EmbeddedServer embeddedServer) {
            this.proxyHttpClient = proxyHttpClient
            this.embeddedServer = embeddedServer
        }

        @Get("/relay")
        Publisher<MutableHttpResponse<?>> relay(HttpRequest<?> request) {
            return proxyHttpClient.proxy(request.mutate().uri(URI.create(embeddedServer.URI.toString() + "/proxy-cookie-filter-upstream/cookies")))
        }
    }

    @Controller("/proxy-cookie-filter-upstream")
    @Requires(property = "spec.name", value = "ProxyClientFilterAddsCookieSpec")
    static class UpstreamController {
        @Get(value = "/cookies", produces = MediaType.TEXT_PLAIN)
        String cookies(@CookieValue("sent") String sent, @CookieValue("added") String added) {
            return sent + " " + added
        }
    }

    @ClientFilter("/proxy-cookie-filter-upstream/**")
    @Requires(property = "spec.name", value = "ProxyClientFilterAddsCookieSpec")
    static class AddCookieFilter {
        @RequestFilter
        void addCookie(MutableHttpRequest<?> request) {
            request.cookie(Cookie.of("added", "by-filter"))
        }
    }
}
