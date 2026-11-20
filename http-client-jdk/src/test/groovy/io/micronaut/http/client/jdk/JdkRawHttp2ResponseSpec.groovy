package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.client.RawRequestOptions
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class JdkRawHttp2ResponseSpec extends Specification {

    void "a response received over HTTP/2 is returned by the raw client"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                    : 'JdkRawHttp2ResponseSpec',
                'micronaut.server.http-version': '2.0',
        ])
        ApplicationContext ctx = ApplicationContext.run()
        RawHttpClient client = ctx.createBean(RawHttpClient)

        when:
        def response = (ByteBodyHttpResponse<?>) Mono.from(client.exchange(HttpRequest.GET(server.URI.resolve('/version')), null, null, RawRequestOptions.proxy()))
                .toFuture().get(10, TimeUnit.SECONDS)

        then:
        response.code() == 200
        !response.headers.names().any { it.startsWith(':') }
        response.byteBody().buffer().get(10, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8) == 'HTTP_2_0'

        cleanup:
        client?.close()
        ctx.close()
        server.close()
    }

    @Requires(property = 'spec.name', value = 'JdkRawHttp2ResponseSpec')
    @Controller
    static class VersionController {
        @Get('/version')
        String version(HttpRequest<?> request) {
            return request.httpVersion.name()
        }
    }
}
