package io.micronaut.http.client


import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import jakarta.inject.Inject
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import spock.lang.Specification

@MicronautTest
class ExceptionOnClientClosedTest extends Specification {
    static final HTTP_EXCEPTION_MESSAGE = "Client '/test': The client is closed, unable to send request."

    @Inject
    @Client("/test")
    HttpClient httpClient

    @Inject
    @Client("/ws/test")
    WebSocketClient webSocketClient

    void "test http client returns error if closed in blocking mode"() {
        given:
        httpClient.close()

        when:
        def result = httpClient.toBlocking().exchange("/")

        then: "an error is returned indicating the client is closed"
        def ex = thrown HttpClientException
        ex.serviceId == "/test"
        ex.message == HTTP_EXCEPTION_MESSAGE
        result == null
    }

    void "test http client returns error if closed in non-blocking mode"() {
        given:
        httpClient.close()

        when:
        def result = Mono.from(httpClient.exchange("/"))

        then:
        StepVerifier.create(result)
                .expectErrorMatches { throwable ->
                    throwable instanceof HttpClientException &&
                            throwable.serviceId == "/test" &&
                            throwable.message == HTTP_EXCEPTION_MESSAGE
                }
                .verify()
    }

    void "test websocket client returns error if closed"() {
        given:
        webSocketClient.close()

        when:
        def res = Mono.from(webSocketClient.connect(StubWebSocketClient.class, 'https://websocket-echo.com'))

        then:
        StepVerifier.create(res)
                .expectErrorMatches { throwable ->
                    throwable instanceof HttpClientException &&
                            throwable.serviceId == "/ws/test" &&
                            throwable.message == "Client '/ws/test': The client is closed, unable to connect for websocket."
                }
                .verify()
    }

    @ClientWebSocket
    static class StubWebSocketClient implements AutoCloseable {
        @OnOpen
        void onOpen(WebSocketSession session) {}

        @OnMessage
        void onMessage(String text) {}

        @OnClose
        void onClose() {}

        @Override
        void close() {}
    }
}
