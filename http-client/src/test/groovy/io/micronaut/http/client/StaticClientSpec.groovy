package io.micronaut.http.client

import io.micronaut.http.client.netty.DefaultHttpClient
import io.micronaut.http.client.sse.SseClient
import io.micronaut.websocket.WebSocketClient
import spock.lang.Specification

class StaticClientSpec extends Specification {

    void "test clients can be created outside the context"() {
        URL url = new URL("https://foo_bar")

        expect:
        HttpClient.create(url) instanceof DefaultHttpClient
        StreamingHttpClient.create(url) instanceof DefaultHttpClient
        SseClient.create(url) instanceof DefaultHttpClient
        ProxyHttpClient.create(url) instanceof DefaultHttpClient
        WebSocketClient.create(url) instanceof DefaultHttpClient
    }
}
