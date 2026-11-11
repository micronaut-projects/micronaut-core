package io.micronaut.http.client.netty

import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.websocket.AsyncWebSocketClient
import spock.lang.Specification

class AsyncWebSocketClientFactorySpec extends Specification {

    void "an async websocket client can be created outside the context"() {
        given:
        URI uri = URI.create("http://localhost:8080")

        when:
        AsyncWebSocketClient client = AsyncWebSocketClient.create(uri)
        AsyncWebSocketClient configured = AsyncWebSocketClient.create(uri, new DefaultHttpClientConfiguration())
        AsyncWebSocketClient fromFactory = new NettyHttpClientFactory().createAsyncWebSocketClient(uri)

        then:
        client instanceof NettyAsyncWebSocketClient
        configured instanceof NettyAsyncWebSocketClient
        fromFactory instanceof NettyAsyncWebSocketClient

        cleanup:
        client?.close()
        configured?.close()
        fromFactory?.close()
    }
}
