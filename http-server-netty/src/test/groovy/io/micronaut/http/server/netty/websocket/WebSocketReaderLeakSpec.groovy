package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Consumes
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import org.reactivestreams.Publisher
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.TimeUnit

class WebSocketReaderLeakSpec extends Specification {

    void "a message the reader fails to decode is released"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'WebSocketReaderLeakSpec'])
        FailingMessageBodyReader reader = embeddedServer.applicationContext.getBean(FailingMessageBodyReader)
        HttpClient httpClient = HttpClient.newHttpClient()
        def listener = new FragmentedMessageWebSocketSpec.CollectingListener()
        WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:${embeddedServer.port}/reader-leak"), listener)
                .get(10, TimeUnit.SECONDS)

        when:
        for (int i = 0; i < fragments.size(); i++) {
            ws.sendText(fragments[i], i == fragments.size() - 1).get(10, TimeUnit.SECONDS)
        }

        then:
        new PollingConditions(timeout: 10).eventually {
            listener.closeCode == 1011
        }
        listener.messages.empty
        reader.buffers.size() == 1
        reader.buffers[0].refCnt() == 0

        cleanup:
        ws?.abort()
        httpClient?.close()
        embeddedServer?.close()

        where:
        fragments << [
                ['{"text":"hello"}'],
                ['{"text":', '"hello"}'],
        ]
    }

    @Requires(property = 'spec.name', value = 'WebSocketReaderLeakSpec')
    @ServerWebSocket("/reader-leak")
    static class FailingServerWebSocket {
        @OnMessage
        @Consumes(FailingMessageBodyReader.MEDIA_TYPE)
        Publisher<String> onMessage(Message message, WebSocketSession session) {
            return session.send(message.text)
        }
    }
}
