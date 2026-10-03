package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.ProxyHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * A client filter of the proxy client replaces the body of the proxied server request. The body
 * bytes the proxy claimed from the server request are released (the spec runs with the leak
 * detector).
 */
class ProxyClientFilterReplacesBodySpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ProxyClientFilterReplacesBodySpec'])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "a client filter replaces the body of a proxied server request of #size bytes"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.POST("/proxy-filter/relay", "x" * size).contentType(MediaType.TEXT_PLAIN_TYPE)) == "replacement"

        where:
        size << [8, 256 * 1024]
    }

    @Controller("/proxy-filter")
    @Requires(property = "spec.name", value = "ProxyClientFilterReplacesBodySpec")
    static class RelayController {
        private final ProxyHttpClient proxyHttpClient
        private final EmbeddedServer embeddedServer

        RelayController(ProxyHttpClient proxyHttpClient, EmbeddedServer embeddedServer) {
            this.proxyHttpClient = proxyHttpClient
            this.embeddedServer = embeddedServer
        }

        @Post(value = "/relay", consumes = MediaType.ALL)
        Publisher<MutableHttpResponse<?>> relay(HttpRequest<?> request) {
            return proxyHttpClient.proxy(request.mutate().uri(URI.create(embeddedServer.URI.toString() + "/proxy-upstream/echo")))
        }
    }

    @Controller("/proxy-upstream")
    @Requires(property = "spec.name", value = "ProxyClientFilterReplacesBodySpec")
    static class UpstreamController {
        @Post(value = "/echo", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String echo(@Body String body) {
            return body
        }
    }

    @ClientFilter("/proxy-upstream/**")
    @Requires(property = "spec.name", value = "ProxyClientFilterReplacesBodySpec")
    static class ReplaceBodyFilter {
        @RequestFilter
        void replace(MutableHttpRequest<?> request) {
            request.body("replacement")
        }
    }
}
