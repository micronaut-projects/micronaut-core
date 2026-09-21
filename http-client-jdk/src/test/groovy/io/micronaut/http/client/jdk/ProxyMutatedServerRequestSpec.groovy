package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.ProxyHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ProxyMutatedServerRequestSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ProxyMutatedServerRequestSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the JDK proxy client relays the body of a mutated server request"() {
        expect:
        server.applicationContext.getBean(ProxyHttpClient) instanceof JdkRawHttpClient
        client.toBlocking().retrieve(HttpRequest.POST("/relay", "hello").contentType(MediaType.TEXT_PLAIN_TYPE)) == "hello"
    }

    void "the JDK proxy client sends the body that replaced the body of a mutated server request"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.POST("/relay-replaced", "hello").contentType(MediaType.TEXT_PLAIN_TYPE)) == "replaced"
    }

    @Controller
    @Requires(property = "spec.name", value = "ProxyMutatedServerRequestSpec")
    static class RelayController {
        private final ProxyHttpClient proxyHttpClient
        private final EmbeddedServer embeddedServer

        RelayController(ProxyHttpClient proxyHttpClient, EmbeddedServer embeddedServer) {
            this.proxyHttpClient = proxyHttpClient
            this.embeddedServer = embeddedServer
        }

        @Post(value = "/relay", consumes = MediaType.ALL)
        Publisher<MutableHttpResponse<?>> relay(HttpRequest<?> request) {
            return proxyHttpClient.proxy(request.mutate().uri(URI.create(embeddedServer.URI.toString() + "/echo")))
        }

        @Post(value = "/relay-replaced", consumes = MediaType.ALL)
        Publisher<MutableHttpResponse<?>> relayReplaced(HttpRequest<?> request) {
            return proxyHttpClient.proxy(request.mutate().uri(URI.create(embeddedServer.URI.toString() + "/echo")).body("replaced"))
        }

        @Post(value = "/echo", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String echo(@Body String body) {
            return body
        }
    }
}
