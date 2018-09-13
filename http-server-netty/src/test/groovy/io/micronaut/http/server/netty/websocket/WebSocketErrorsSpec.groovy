package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.http.server.netty.websocket.errors.ErrorsClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.CloseReason
import io.micronaut.websocket.RxWebSocketClient
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class WebSocketErrorsSpec extends Specification {

    void "test error from on message handler without @OnMessage closes the connection"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        RxWebSocketClient wsClient = embeddedServer.applicationContext.createBean(RxWebSocketClient, embeddedServer.getURI())
        PollingConditions conditions = new PollingConditions(timeout: 15    , delay: 0.5)

        ErrorsClient client = wsClient.connect(ErrorsClient, "/ws/errors/message").blockingFirst()

        when:
        client.send("foo")

        then:
        conditions.eventually {
            !client.session.isOpen()
            client.lastReason != null
            client.lastReason == CloseReason.INTERNAL_ERROR
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
