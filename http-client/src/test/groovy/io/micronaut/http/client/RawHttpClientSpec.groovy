package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.body.ByteBody
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import spock.lang.Issue
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class RawHttpClientSpec extends Specification {
    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/11603")
    def 'immediate stream'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'RawHttpClientSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(RawHttpClient, server.URI)

        def requestBody = new NettyByteBodyFactory(new EmbeddedChannel()).adaptNetty(
                Mono.just(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)))
        requestBody.split(ByteBody.SplitBackpressureMode.FASTEST).buffer().get().close()

        when:
        def resp = Mono.from(client.exchange(
                HttpRequest.POST(server.URI.toString() + "/raw/echo", null),
                requestBody,
                null
        )).block()
        then:
        resp instanceof ByteBodyHttpResponse<?>
        ByteBody body = resp.byteBody()
        body.buffer().get().toString(StandardCharsets.UTF_8) == "foo"

        cleanup:
        resp.close()
        server.close()
        ctx.close()
    }

    @Singleton
    @Controller("/raw")
    @Requires(property = "spec.name", value = "RawHttpClientSpec")
    static class Ctrl {
        @Post("/echo")
        String echo(@Body String body) {
            return body
        }
    }
}
