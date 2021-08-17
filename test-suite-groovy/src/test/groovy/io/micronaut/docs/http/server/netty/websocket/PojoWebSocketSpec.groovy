package io.micronaut.docs.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class PojoWebSocketSpec extends Specification {

    void "test POJO websocket exchange"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder('micronaut.server.netty.log-level':'TRACE').run(EmbeddedServer)
        PollingConditions conditions = new PollingConditions(timeout: 15, delay: 0.5)

        when: "a websocket connection is established"
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURI())
        PojoChatClientWebSocket fred = Flux.from(wsClient.connect(PojoChatClientWebSocket, "/pojo/chat/stuff/fred")).blockFirst()
        PojoChatClientWebSocket bob = Flux.from(wsClient.connect(PojoChatClientWebSocket, [topic:"stuff", username:"bob"])).blockFirst()

        then:"A session is established"
        fred.topic == 'stuff'
        fred.username == 'fred'
        bob.username == 'bob'

        conditions.eventually {
            fred.replies.contains(new Message(text:"[bob] Joined!"))
            fred.replies.size() == 1
        }

        when:"A message is sent"
        fred.send(new Message(text: "Hello bob!"))

        then:
        conditions.eventually {
            bob.replies.contains(new Message(text:"[fred] Hello bob!"))
            fred.replies.contains(new Message(text:"[bob] Joined!"))
            !fred.replies.contains(new Message(text:"[fred] Hello bob!"))
            !bob.replies.contains(new Message(text:"[bob] Joined!"))
        }

        when:
        bob.send(new Message(text: "Hi fred. How are things?"))

        then:
        conditions.eventually {
            fred.replies.contains(new Message(text:"[bob] Hi fred. How are things?"))
            !bob.replies.contains(new Message(text:"[bob] Hi fred. How are things?"))
            bob.replies.contains(new Message(text:"[fred] Hello bob!"))
        }
        fred.sendAsync(new Message(text:  "foo")).get().text == 'foo'
        Mono.from(fred.sendRx(new Message(text:  "bar"))).block().text == 'bar'

        cleanup:
        bob?.close()
        fred?.close()

        wsClient.close()
        embeddedServer.close()
    }
}
