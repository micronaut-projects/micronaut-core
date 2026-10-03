package io.micronaut.http.client.jdk

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
 * A client filter of the JDK proxy client replaces the body of the proxied server request.
 */
class JdkProxyClientFilterReplacesBodySpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'JdkProxyClientFilterReplacesBodySpec'])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "a client filter replaces the body of a proxied server request of #size bytes"() {
        expect:
        server.applicationContext.getBean(ProxyHttpClient) instanceof JdkRawHttpClient
        client.toBlocking().retrieve(HttpRequest.POST("/jdk-proxy-filter/relay", "x" * size).contentType(MediaType.TEXT_PLAIN_TYPE)) == "replacement"

        where:
        size << [8, 256 * 1024]
    }

    @Controller("/jdk-proxy-filter")
    @Requires(property = "spec.name", value = "JdkProxyClientFilterReplacesBodySpec")
    static class RelayController {
        private final ProxyHttpClient proxyHttpClient
        private final EmbeddedServer embeddedServer

        RelayController(ProxyHttpClient proxyHttpClient, EmbeddedServer embeddedServer) {
            this.proxyHttpClient = proxyHttpClient
            this.embeddedServer = embeddedServer
        }

        @Post(value = "/relay", consumes = MediaType.ALL)
        Publisher<MutableHttpResponse<?>> relay(HttpRequest<?> request) {
            return proxyHttpClient.proxy(request.mutate().uri(URI.create(embeddedServer.URI.toString() + "/jdk-proxy-upstream/echo")))
        }
    }

    @Controller("/jdk-proxy-upstream")
    @Requires(property = "spec.name", value = "JdkProxyClientFilterReplacesBodySpec")
    static class UpstreamController {
        @Post(value = "/echo", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String echo(@Body String body) {
            return body
        }
    }

    @ClientFilter("/jdk-proxy-upstream/**")
    @Requires(property = "spec.name", value = "JdkProxyClientFilterReplacesBodySpec")
    static class ReplaceBodyFilter {
        @RequestFilter
        void replace(MutableHttpRequest<?> request) {
            request.body("replacement")
        }
    }
}
