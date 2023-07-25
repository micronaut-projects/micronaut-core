package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanProvider
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.ProxyHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification

class TransferSpec extends Specification {
    def 'transfer headers'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'TransferSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        // use java http client because the micronaut one strips some of these headers
        def client = java.net.http.HttpClient.newHttpClient()

        when:
        def clResponse = client.send(
                java.net.http.HttpRequest.newBuilder(new URI(server.URI.toString() + "/explicit-content-length")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString())
        then:
        clResponse.body() == "foo"
        clResponse.headers().firstValue("content-length").isEmpty()
        clResponse.headers().firstValue("transfer-encoding").get() == "chunked"

        when:
        def teResponse = client.send(
                java.net.http.HttpRequest.newBuilder(new URI(server.URI.toString() + "/explicit-transfer")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString())
        then:
        teResponse.body() == "foo"
        teResponse.headers().firstValue("content-length").get() == "3"
        teResponse.headers().firstValue("transfer-encoding").isEmpty()

        when:
        def proxyClResponse = client.send(
                java.net.http.HttpRequest.newBuilder(new URI(server.URI.toString() + "/proxied/explicit-content-length")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString())
        then:
        proxyClResponse.body() == "foo"
        proxyClResponse.headers().firstValue("content-length").isEmpty()
        proxyClResponse.headers().firstValue("transfer-encoding").get() == "chunked"

        when:
        def proxyTeResponse = client.send(
                java.net.http.HttpRequest.newBuilder(new URI(server.URI.toString() + "/proxied/explicit-transfer")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString())
        then:
        proxyTeResponse.body() == "foo"
        proxyTeResponse.headers().firstValue("content-length").isEmpty()
        proxyTeResponse.headers().firstValue("transfer-encoding").get() == "chunked"

        when:
        def proxySimpleResponse = client.send(
                java.net.http.HttpRequest.newBuilder(new URI(server.URI.toString() + "/proxied/simple")).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString())
        then:
        proxySimpleResponse.body() == "foo"
        proxySimpleResponse.headers().firstValue("content-length").isEmpty()
        proxySimpleResponse.headers().firstValue("transfer-encoding").get() == "chunked"

        cleanup:
        server.close()
        ctx.close()
    }

    @Controller
    @Requires(property = "spec.name", value = "TransferSpec")
    static class MyController {
        @Inject
        BeanProvider<EmbeddedServer> embeddedServer
        @Inject
        ProxyHttpClient proxyHttpClient

        @Get("/explicit-content-length")
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<Publisher<String>> explicitContentLength() {
            return HttpResponse.ok(Flux.just("f", "oo"))
                    .header("content-length", "3")
        }

        @Get("/explicit-transfer")
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<String> explicitTransfer() {
            return HttpResponse.ok("foo")
                    .header("transfer-encoding", "chunked")
        }

        @Get("/simple")
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<String> simple() {
            return HttpResponse.ok("foo")
        }

        @Get("/proxied/explicit-content-length")
        @Produces(MediaType.TEXT_PLAIN)
        Publisher<HttpResponse<?>> proxiedExplicitContentLength() {
            return proxyHttpClient.proxy(HttpRequest.GET(embeddedServer.get().URI.toString() + "/explicit-content-length"))
        }

        @Get("/proxied/explicit-transfer")
        @Produces(MediaType.TEXT_PLAIN)
        Publisher<HttpResponse<?>> proxiedExplicitTransfer() {
            return proxyHttpClient.proxy(HttpRequest.GET(embeddedServer.get().URI.toString() + "/explicit-transfer"))
        }

        @Get("/proxied/simple")
        @Produces(MediaType.TEXT_PLAIN)
        Publisher<HttpResponse<?>> proxiedSimple() {
            return proxyHttpClient.proxy(HttpRequest.GET(embeddedServer.get().URI.toString() + "/simple"))
        }
    }
}
