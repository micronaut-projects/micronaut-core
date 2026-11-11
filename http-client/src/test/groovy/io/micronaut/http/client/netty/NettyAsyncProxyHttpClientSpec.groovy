package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.client.AsyncProxyHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.ProxyHttpClient
import io.micronaut.http.client.ProxyRequestOptions
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class NettyAsyncProxyHttpClientSpec extends Specification {

    private static String text(MutableHttpResponse<?> response) {
        try (CloseableByteBody body = ((ByteBodyHttpResponse<?>) response).byteBody().move()) {
            return body.buffer().get(10, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8)
        }
    }
    static final String SPEC_NAME = 'NettyAsyncProxyHttpClientSpec'

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC_NAME])

    void "the injected async proxy client is the Netty one and proxies a request"() {
        given:
        AsyncProxyHttpClient client = server.applicationContext.getBean(AsyncProxyHttpClient)

        when:
        MutableHttpResponse<?> response = client.proxy(HttpRequest.GET(server.URI.toString() + '/netty-async-proxy/hello'))
            .toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        client instanceof NettyAsyncProxyHttpClient
        response.status() == HttpStatus.OK
        text(response) == 'hello'
    }

    void "the async view of a created client proxies a request with a body and closes the client"() {
        given:
        HttpClient httpClient = HttpClient.create(server.URL)
        AsyncProxyHttpClient client = ((ProxyHttpClient) httpClient).toAsyncProxy()

        when:
        MutableHttpResponse<?> response = client.proxy(HttpRequest.POST(server.URI.toString() + '/netty-async-proxy/echo', 'body'),
            ProxyRequestOptions.builder().retainHostHeader(true).build()).toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        text(response) == 'body'

        when:
        client.close()

        then:
        !httpClient.isRunning()
    }

    void "a created async proxy client requires options"() {
        given:
        AsyncProxyHttpClient client = AsyncProxyHttpClient.create(server.URI)

        when:
        client.proxy(HttpRequest.GET('/netty-async-proxy/hello'), null)

        then:
        thrown(NullPointerException)

        cleanup:
        client?.close()
    }

    @Controller('/netty-async-proxy')
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class AsyncProxyController {
        @Get('/hello')
        String hello() {
            return 'hello'
        }

        @Post('/echo')
        String echo(@Body String body) {
            return body
        }
    }
}
