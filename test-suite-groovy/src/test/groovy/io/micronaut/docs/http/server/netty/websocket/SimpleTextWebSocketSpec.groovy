package io.micronaut.docs.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Retry
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import jakarta.inject.Inject
import jakarta.inject.Singleton

class SimpleTextWebSocketSpec extends Specification {

    @Retry
    void "test simple text websocket exchange"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder('micronaut.server.netty.log-level':'TRACE').run(EmbeddedServer)
        PollingConditions conditions = new PollingConditions(timeout: 15    , delay: 0.5)

        when: "a websocket connection is established"
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURI())
        ChatClientWebSocket fred = Flux.from(wsClient.connect(ChatClientWebSocket, "/chat/stuff/fred")).blockFirst()
        Thread.sleep(50)
        ChatClientWebSocket bob = Flux.from(wsClient.connect(ChatClientWebSocket, [topic:"stuff", username:"bob"])).blockFirst()

        then:"The connection is valid"
        fred.session != null
        fred.session.id != null
        fred.request != null

        then:"A session is established"
        fred.session != null
        fred.session.id != null
        fred.session.id != bob.session.id
        fred.request != null
        fred.topic == 'stuff'
        fred.username == 'fred'
        bob.username == 'bob'
        conditions.eventually {
            fred.replies.contains("[bob] Joined!")
            fred.replies.size() == 1
        }


        when:"A message is sent"
        fred.send("Hello bob!")

        then:
        conditions.eventually {
            bob.replies.contains("[fred] Hello bob!")
            bob.replies.size() == 1
        }

        when:
        bob.send("Hi fred. How are things?")

        then:
        conditions.eventually {

            fred.replies.contains("[bob] Hi fred. How are things?")
            fred.replies.size() == 2
            bob.replies.contains("[fred] Hello bob!")
            bob.replies.size() == 1
        }
        fred.sendAsync("foo").get() == 'foo'
        Mono.from(fred.sendRx("bar")).block() == 'bar'

        when:
        bob.close()
        fred.close()

        then:
        conditions.eventually {
            !bob.session.isOpen()
            !fred.session.isOpen()
        }

        when:"A bean is retrieved that injects a websocket client"
        MyBean myBean = embeddedServer.applicationContext.getBean(MyBean)

        then:
        myBean.myClient != null

        cleanup:
        wsClient.close()
        embeddedServer.close()
    }

    @Singleton
    static class MyBean {
        @Inject
        @Client("http://localhost:8080")
        WebSocketClient myClient
    }
}
