package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.RxWebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.reactivex.Single
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future

class BinaryWebSocketSpec extends Specification {

    void "test binary websocket exchange"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.5)

        when: "a websocket connection is established"
        RxWebSocketClient wsClient = embeddedServer.applicationContext.createBean(RxWebSocketClient, embeddedServer.getURI())
        BinaryChatClientWebSocket fred = wsClient.connect(BinaryChatClientWebSocket, "/binary/chat/stuff/fred").blockingFirst()
        BinaryChatClientWebSocket bob = wsClient.connect(BinaryChatClientWebSocket, [topic:"stuff",username:"bob"]).blockingFirst()

        then:"A session is established"
        fred.topic == 'stuff'
        fred.username == 'fred'
        bob.username == 'bob'



        when:"A message is sent"
        fred.send("Hello bob!".bytes)

        then:
        conditions.eventually {
            bob.replies.contains("[fred] Hello bob!".bytes)
            bob.replies.size() == 1
            fred.replies.contains("[bob] Joined!".bytes)
            fred.replies.size() == 1
        }

        when:
        bob.send("Hi fred. How are things?".bytes)

        then:
        conditions.eventually {

            fred.replies.contains(new Message(text:"[bob] Hi fred. How are things?"))
            fred.replies.size() == 2
            bob.replies.contains(new Message(text:"[fred] Hello bob!"))
            bob.replies.size() == 1
        }
        fred.sendAsync(Unpooled.wrappedBuffer("foo".bytes)).get().toString(StandardCharsets.UTF_8) == 'foo'
        new String( fred.sendRx(ByteBuffer.wrap("bar".bytes)).blockingGet().array() ) == 'bar'

        cleanup:
        bob?.close()
        fred?.close()

        wsClient.close()
        embeddedServer.close()
    }
    @ClientWebSocket("/binary/chat/{topic}/{username}")
    static abstract class BinaryChatClientWebSocket implements AutoCloseable {

        private String topic
        private String username
        private Collection<String> replies = new ConcurrentLinkedQueue<>()

        @OnOpen
        void onOpen(String topic, String username) {
            this.topic = topic
            this.username = username
        }

        String getTopic() {
            return topic
        }

        String getUsername() {
            return username
        }

        Collection<Message> getReplies() {
            return replies
        }

        @OnMessage
        void onMessage(
                byte[] message) {
            System.out.println("Client received message = " + message)
            replies.add(new String(message, StandardCharsets.UTF_8))
        }

        abstract void send(byte[] message);

        abstract Future<ByteBuf> sendAsync(ByteBuf message);

        abstract Single<ByteBuffer> sendRx(ByteBuffer message);
    }


    @ServerWebSocket("/binary/chat/{topic}/{username}")
    static class BinaryChatServerWebSocket {

        @OnOpen
        void onOpen(String topic, String username, WebSocketSession session) {
            Set<? extends WebSocketSession> openSessions = session.getOpenSessions()
            System.out.println("Server session opened for username = " + username)
            System.out.println("Server openSessions = " + openSessions)
            for (WebSocketSession openSession : openSessions) {
                if(isValid(topic, session, openSession)) {
                    String msg = "[" + username + "] Joined!"
                    System.out.println("Server sending msg = " + msg)
                    openSession.sendSync(msg.bytes)
                }
            }
        }

        @OnMessage
        void onMessage(
                String topic,
                String username,
                byte[] message,
                WebSocketSession session) {

            Set<? extends WebSocketSession> openSessions = session.getOpenSessions()
            System.out.println("Server received message = " + message)
            System.out.println("Server openSessions = " + openSessions)
            for (WebSocketSession openSession : openSessions) {
                if(isValid(topic, session, openSession)) {
                    String msg = "[" + username + "] " + new String(message, StandardCharsets.UTF_8)
                    System.out.println("Server sending msg = " + msg)
                    openSession.sendSync(msg.bytes)
                }
            }
        }

        @OnClose
        void onClose(
                String topic,
                String username,
                WebSocketSession session) {
            Set<? extends WebSocketSession> openSessions = session.getOpenSessions()
            System.out.println("Server session closing for username = " + username)
            for (WebSocketSession openSession : openSessions) {
                if(isValid(topic, session, openSession)) {
                    String msg = "[" + username + "] Disconnected!"
                    System.out.println("Server sending msg = " + msg)
                    openSession.sendSync(msg.bytes)
                }
            }
        }

        private boolean isValid(String topic, WebSocketSession session, WebSocketSession openSession) {
            return openSession != session && topic.equalsIgnoreCase(openSession.getUriVariables().get("topic", String.class).orElse(null))
        }
    }
}
