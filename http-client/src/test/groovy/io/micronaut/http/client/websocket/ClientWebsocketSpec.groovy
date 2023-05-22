package io.micronaut.http.client.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.exceptions.WebSocketClientException
import jakarta.inject.Inject
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.ExecutionException

class ClientWebsocketSpec extends Specification {
    void 'websocket bean should not open if there is a connection error'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'ClientWebsocketSpec'])
        def client = ctx.getBean(WebSocketClient)
        def registry = ctx.getBean(ClientBeanRegistry)
        def mono = Mono.from(client.connect(ClientBean.class, 'http://does-not-exist'))

        when:
        mono.toFuture().get()
        then:
        def e = thrown ExecutionException
        e.cause instanceof WebSocketClientException

        registry.clientBeans.size() == 1
        !registry.clientBeans[0].opened
        !registry.clientBeans[0].autoClosed
        !registry.clientBeans[0].onClosed

        cleanup:
        client.close()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ClientWebsocketSpec')
    static class ClientBeanRegistry {
        List<ClientBean> clientBeans = new ArrayList<>()
    }

    @ClientWebSocket
    static class ClientBean implements AutoCloseable {
        boolean opened = false
        boolean onClosed = false
        boolean autoClosed = false

        @Inject
        ClientBean(ClientBeanRegistry registry) {
            registry.clientBeans.add(this)
        }

        @OnOpen
        void open() {
            opened = true
        }

        @OnMessage
        void onMessage(String text) {
        }

        @OnClose
        void onClose() {
            onClosed = true
        }

        @Override
        void close() throws Exception {
            autoClosed = true
        }
    }
}
