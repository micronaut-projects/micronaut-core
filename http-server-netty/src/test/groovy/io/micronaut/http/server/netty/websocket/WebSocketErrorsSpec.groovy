/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.http.server.netty.websocket.errors.ErrorsClient
import io.micronaut.http.server.netty.websocket.errors.MessageErrorSocket
import io.micronaut.http.server.netty.websocket.errors.TimeoutErrorSocket
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.CloseReason
import io.micronaut.websocket.WebSocketClient
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class WebSocketErrorsSpec extends Specification {

    void "test idle timeout invokes onclose"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.idle-timeout': '5s'
        ])
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURI())
        PollingConditions conditions = new PollingConditions(timeout: 15, delay: 0.5)

        when:
        TimeoutErrorSocket errorSocket = embeddedServer.applicationContext.getBean(TimeoutErrorSocket)

        then:
        !errorSocket.isClosed()

        ErrorsClient client = Flux.from(wsClient.connect(ErrorsClient, "/ws/timeout/message")).blockFirst()

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
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURI())
        PollingConditions conditions = new PollingConditions(timeout: 15, delay: 0.5)

        when:
        MessageErrorSocket errorSocket = embeddedServer.applicationContext.getBean(MessageErrorSocket)

        then:
        !errorSocket.isClosed()

        ErrorsClient client = Flux.from(wsClient.connect(ErrorsClient, "/ws/errors/message")).blockFirst()

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
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURI())
        PollingConditions conditions = new PollingConditions(timeout: 15    , delay: 0.5)

        ErrorsClient client = Flux.from(wsClient.connect(ErrorsClient, "/ws/errors/message-onerror")).blockFirst()

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
