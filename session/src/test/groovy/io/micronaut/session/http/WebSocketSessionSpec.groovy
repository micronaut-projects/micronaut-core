package io.micronaut.session.http

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.session.Session
import io.micronaut.websocket.RxWebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class WebSocketSessionSpec extends Specification {


    void "test websocket can share HTTP session"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

        RxWebSocketClient wsClient = embeddedServer.applicationContext.createBean(RxWebSocketClient, embeddedServer.getURL())
        RxHttpClient httpClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

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
        SomeValueClient someValueClient= wsClient.connect(SomeValueClient, HttpRequest.GET('/ws/somesocket').header(HttpHeaders.AUTHORIZATION_INFO, sessionId)).blockingFirst()
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
