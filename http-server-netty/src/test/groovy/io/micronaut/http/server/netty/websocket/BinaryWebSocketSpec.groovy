package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.RxWebSocketClient
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class BinaryWebSocketSpec extends Specification{
    void "test binary websocket exchange"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.build('micronaut.server.netty.log-level':'TRACE').run(EmbeddedServer)
        PollingConditions conditions = new PollingConditions(timeout: 15, delay: 0.5)

        when: "a websocket connection is established"
        RxWebSocketClient wsClient = embeddedServer.applicationContext.createBean(RxWebSocketClient, embeddedServer.getURI())
        BinaryChatClientWebSocket fred = wsClient.connect(BinaryChatClientWebSocket, "/binary/chat/stuff/fred").blockingFirst()
        BinaryChatClientWebSocket bob = wsClient.connect(BinaryChatClientWebSocket, [topic:"stuff",username:"bob"]).blockingFirst()

        then:"The connection is valid"
        fred.session != null
        fred.session.id != null



        then:"A session is established"
        fred.session != null
        fred.session.id != null
        fred.session.id != bob.session.id
        fred.topic == 'stuff'
        fred.username == 'fred'
        bob.username == 'bob'



        when:"A message is sent"
        fred.send("Hello bob!".bytes)

        then:
        conditions.eventually {
            bob.replies.contains("[fred] Hello bob!")
            bob.replies.size() == 1
            fred.replies.contains("[bob] Joined!")
            fred.replies.size() == 1
        }

        when:
        bob.send("Hi fred. How are things?".bytes)

        then:
        conditions.eventually {

            fred.replies.contains("[bob] Hi fred. How are things?")
            fred.replies.size() == 2
            bob.replies.contains("[fred] Hello bob!")
            bob.replies.size() == 1
        }
        def buffer = Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)
        buffer.retain()
        fred.sendAsync(buffer).get().toString(StandardCharsets.UTF_8) == 'foo'
        new String(fred.sendRx(ByteBuffer.wrap("bar".bytes)).blockingGet().array()) == 'bar'

        when:
        bob.close()


        then:
        conditions.eventually {
            !bob.session.isOpen()
        }

        when:
        fred.send("Damn bob left".bytes)

        then:
        conditions.eventually {
            fred.replies.contains("[bob] Disconnected!")
            !bob.replies.contains("[bob] Disconnected!")
        }

        cleanup:
        wsClient.close()
        embeddedServer.close()
    }
}
