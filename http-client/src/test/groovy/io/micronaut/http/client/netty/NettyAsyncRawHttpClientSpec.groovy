package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.AsyncRawHttpClient
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.client.RawHttpClientRegistry
import io.micronaut.http.client.HttpVersionSelection
import io.micronaut.http.HttpVersion
import spock.lang.Specification

class NettyAsyncRawHttpClientSpec extends Specification {

    void "the Netty client completes async raw exchanges from its execution flow, without the publisher adapter"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        expect:
        ctx.getBean(AsyncRawHttpClient) instanceof NettyAsyncRawHttpClient
        ctx.getBean(RawHttpClient).toAsyncRaw() instanceof NettyAsyncRawHttpClient
        ctx.getBean(RawHttpClientRegistry).getAsyncRawClient(HttpVersionSelection.forLegacyVersion(HttpVersion.HTTP_1_1), "http://localhost", null) instanceof NettyAsyncRawHttpClient

        when:
        AsyncRawHttpClient created = AsyncRawHttpClient.create(URI.create("http://localhost"))

        then:
        created instanceof NettyAsyncRawHttpClient

        cleanup:
        created?.close()
        ctx.close()
    }
}
