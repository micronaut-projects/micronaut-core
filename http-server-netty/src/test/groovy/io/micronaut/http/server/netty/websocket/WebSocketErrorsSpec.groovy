package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.http.server.netty.websocket.errors.ErrorsClient
import io.micronaut.http.server.netty.websocket.errors.MessageErrorSocket
import io.micronaut.http.server.netty.websocket.errors.TimeoutErrorSocket
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.CloseReason
import io.micronaut.websocket.RxWebSocketClient
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class WebSocketErrorsSpec extends Specification {

    void "test idle timeout invokes onclose"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.idle-timeout': '5s'
        ])
        RxWebSocketClient wsClient = embeddedServer.applicationContext.createBean(RxWebSocketClient, embeddedServer.getURI())
        PollingConditions conditions = new PollingConditions(timeout: 15, delay: 0.5)

        when:
        TimeoutErrorSocket errorSocket = embeddedServer.applicationContext.getBean(TimeoutErrorSocket)

        then:
        !errorSocket.isClosed()

        ErrorsClient client = wsClient.connect(ErrorsClient, "/ws/timeout/message").blockingFirst()

        when:
        client.send("foo")

        then:"Eventually idle timeout closes the server session"
        conditions.eventually {
            !client.session.isOpen()
            client.lastReason != null
            client.lastReason == CloseReason.GOING_AWAY
            errorSocket.isClosed()
        }

        cleanup:
        wsClient.close()
        embeddedServer.stop()
    }

    void "test error from on message handler without @OnMessage closes the connection"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxWebSocketClient wsClient = embeddedServer.applicationContext.createBean(RxWebSocketClient, embeddedServer.getURI())
        PollingConditions conditions = new PollingConditions(timeout: 15, delay: 0.5)

        when:
        MessageErrorSocket errorSocket = embeddedServer.applicationContext.getBean(MessageErrorSocket)

        then:
        !errorSocket.isClosed()

        ErrorsClient client = wsClient.connect(ErrorsClient, "/ws/errors/message").blockingFirst()

        when:
        client.send("foo")

        then:
        conditions.eventually {
            !client.session.isOpen()
            client.lastReason != null
            client.lastReason == CloseReason.INTERNAL_ERROR
            errorSocket.isClosed()
        }

        cleanup:
        wsClient.close()
        embeddedServer.stop()
    }

    void "test error from on message handler without @OnMessage invokes @OnError handler"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxWebSocketClient wsClient = embeddedServer.applicationContext.createBean(RxWebSocketClient, embeddedServer.getURI())
        PollingConditions conditions = new PollingConditions(timeout: 15    , delay: 0.5)

        ErrorsClient client = wsClient.connect(ErrorsClient, "/ws/errors/message-onerror").blockingFirst()

        when:
        client.send("foo")

        then:
        conditions.eventually {
            !client.session.isOpen()
            client.lastReason != null
            client.lastReason == CloseReason.UNSUPPORTED_DATA
        }

        cleanup:
        wsClient.close()
        embeddedServer.stop()
    }

}
