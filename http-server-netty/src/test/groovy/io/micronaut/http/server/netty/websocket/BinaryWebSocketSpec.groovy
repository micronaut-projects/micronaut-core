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
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.RxWebSocketClient
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import spock.lang.Retry
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

@Retry
class BinaryWebSocketSpec extends Specification{

    @Retry
    void "test binary websocket exchange"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.builder('micronaut.server.netty.log-level':'TRACE').run(EmbeddedServer)
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
        conditions.eventually {
            fred.replies.contains("[bob] Joined!")
            fred.replies.size() == 1
        }


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
        sleep(1000)


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
