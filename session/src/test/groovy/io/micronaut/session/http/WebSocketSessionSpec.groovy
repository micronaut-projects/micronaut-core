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
package io.micronaut.session.http

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.session.Session
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class WebSocketSessionSpec extends Specification {

    void "test websocket can share HTTP session"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURL())
        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse response = httpClient.toBlocking().exchange(HttpRequest.GET('/ws/session/simple'), String)
        String sessionId = response.header(HttpHeaders.AUTHORIZATION_INFO)

        then:
        sessionId

        when:
        def result = httpClient.toBlocking().retrieve(HttpRequest.GET('/ws/session/simple').header(HttpHeaders.AUTHORIZATION_INFO, sessionId), String)

        then:
        result == 'value in session'

        when:
        SomeValueClient someValueClient= Flux.from(wsClient.connect(SomeValueClient, HttpRequest.GET('/ws/somesocket').header(HttpHeaders.AUTHORIZATION_INFO, sessionId))).blockFirst()
        someValueClient.send("hello")
        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.5)

        then:
        conditions.eventually {
            someValueClient.replies.contains("hello value is value in session")
        }

        cleanup:
        wsClient.close()
        httpClient.close()
        embeddedServer.close()
    }

    @Controller('/ws/session')
    static class SessionController {

        @Get("/simple")
        String simple(Session session) {
            return session.get("myValue").orElseGet({
                session.put("myValue", "value in session")
                "not in session"
            })
        }
    }

    @ClientWebSocket('/ws/somesocket')
    static abstract class SomeValueClient {
        List<String> replies = []

        @OnMessage
        void onMessage(String msg) {
            replies.add(msg)
        }

        abstract void send(String msg)
    }

    @ServerWebSocket('/ws/somesocket')
    static class SomeValueSocket {

        @OnMessage
        void onMessage(String msg, WebSocketSession session) {
            session.sendSync("$msg value is ${session.get('myValue', String).orElse(null)}".toString())
        }
    }
}
