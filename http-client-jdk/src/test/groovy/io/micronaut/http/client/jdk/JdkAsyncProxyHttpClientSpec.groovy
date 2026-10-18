package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
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
import io.micronaut.http.client.ProxyRequestOptions
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.ExecutionException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class JdkAsyncProxyHttpClientSpec extends Specification {

    private static String text(MutableHttpResponse<?> response) {
        try (CloseableByteBody body = ((ByteBodyHttpResponse<?>) response).byteBody().move()) {
            return body.buffer().get(10, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8)
        }
    }
    static final String SPEC_NAME = 'JdkAsyncProxyHttpClientSpec'

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
        'spec.name'                       : SPEC_NAME,
        'micronaut.http.client.alpn-modes': 'http/1.1',
    ])

    private static MutableHttpResponse<?> await(AsyncProxyHttpClient client, HttpRequest<?> request) {
        return client.proxy(request).toCompletableFuture().get(10, TimeUnit.SECONDS)
    }

    void "the injected async proxy client is the JDK one and proxies a request"() {
        given:
        AsyncProxyHttpClient client = server.applicationContext.getBean(AsyncProxyHttpClient)

        when:
        MutableHttpResponse<?> response = await(client, HttpRequest.GET(server.URI.toString() + '/async-proxy/hello'))

        then:
        client instanceof JdkAsyncProxyHttpClient
        response.status() == HttpStatus.OK
        text(response) == 'hello'
    }

    void "a created async proxy client proxies a request with a body"() {
        given:
        AsyncProxyHttpClient client = AsyncProxyHttpClient.create(server.URI)

        when:
        MutableHttpResponse<?> response = client.proxy(HttpRequest.POST(server.URI.toString() + '/async-proxy/echo', 'body'), ProxyRequestOptions.getDefault())
            .toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        text(response) == 'body'

        cleanup:
        client?.close()
    }

    void "a request that retains the Host header is refused without the system property"() {
        given:
        AsyncProxyHttpClient client = AsyncProxyHttpClient.create(server.URI)

        when:
        client.proxy(HttpRequest.GET(server.URI.toString() + '/async-proxy/hello').header(HttpHeaders.HOST, 'example.com'),
            ProxyRequestOptions.builder().retainHostHeader(true).build()).toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof HttpClientException

        cleanup:
        client?.close()
    }

    void "a request that cannot be copied fails the stage"() {
        given:
        AsyncProxyHttpClient client = AsyncProxyHttpClient.create(server.URI)

        when:
        client.proxy(HttpRequest.GET('/async-proxy/hello'), null)

        then:
        thrown(NullPointerException)

        cleanup:
        client?.close()
    }

    @Controller('/async-proxy')
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
