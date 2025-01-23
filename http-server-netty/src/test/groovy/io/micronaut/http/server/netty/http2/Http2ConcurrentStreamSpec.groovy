package io.micronaut.http.server.netty.http2

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpVersion
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class Http2ConcurrentStreamSpec extends Specification {
    def 'test concurrent streaming responses'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name'                    : 'Http2ConcurrentStreamSpec',
                'micronaut.server.http-version': '2.0',
                'micronaut.ssl.enabled'        : true,
                'micronaut.server.ssl.port'           : -1,
                'micronaut.ssl.buildSelfSigned': true,
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = WebClient.create(Vertx.vertx(), new WebClientOptions()
                .setSsl(true)
                .setUseAlpn(true)
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setTrustAll(true))

        when:
        HttpResponse<Buffer> r1
        client.get(server.port, server.host, "/http2concurrent/request1").send(ar -> {
            r1 = ar.result()
        })
        HttpResponse<Buffer> r2
        client.get(server.port, server.host, "/http2concurrent/request2").send(ar -> {
            r2 = ar.result()
        })
        client.get(server.port, server.host, "/http2concurrent/triggerCompletion").send(ar -> { })
        then:
        new PollingConditions(timeout: 5).eventually {
            r1 != null
            r2 != null
        }
        r1.bodyAsString() == '["r1: 1","r1: 2"]'
        r2.bodyAsString() == '["r2: 1","r2: 2"]'

        cleanup:
        server.close()
    }

    @Requires(property = 'spec.name', value = 'Http2ConcurrentStreamSpec')
    @Controller('/http2concurrent')
    @Singleton
    static class ConcurrentResponseController {
        FluxSink<String> sink1
        FluxSink<String> sink2

        @Get('/request1')
        def request1() {
            return Flux.<String> create(s ->
                    sink1 = s)
        }

        @Get('/request2')
        def request2() {
            return Flux.<String> create(s ->
                    sink2 = s)
        }

        @Get('/triggerCompletion')
        def triggerCompletion() {
            sink1.next('"r1: 1"')
            sink2.next('"r2: 1"')
            sink1.next('"r1: 2"')
            sink2.next('"r2: 2"')
            sink1.complete()
            sink2.complete()
        }
    }
}
