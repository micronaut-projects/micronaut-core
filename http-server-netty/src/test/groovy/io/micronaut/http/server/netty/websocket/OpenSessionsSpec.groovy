package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.netty.websocket.WebSocketSessionRepository
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class OpenSessionsSpec extends Specification {
    def "open sessions follow the connected clients"() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'OpenSessionsSpec'])
        def server = ctx.getBean(EmbeddedServer).start()
        def group = ((WebSocketSessionRepository) server).channelGroup
        def client = ctx.createBean(WebSocketClient, server.URI)
        def conditions = new PollingConditions(timeout: 10)

        when:
        def first = Mono.from(client.connect(ClientSocket, '/open-sessions')).block()
        def second = Mono.from(client.connect(ClientSocket, '/open-sessions')).block()

        then:
        conditions.eventually {
            group.size() == 2
        }

        when: 'each server session reports its own id and the ids of all open sessions'
        first.send('ids')
        second.send('ids')
        def firstReply = first.replies.poll(10, TimeUnit.SECONDS).split(':')
        def secondReply = second.replies.poll(10, TimeUnit.SECONDS).split(':')
        def firstId = firstReply[0]
        def secondId = secondReply[0]

        then: 'the server sessions see each other'
        firstId != secondId
        firstReply[1].split(',') as Set == [firstId, secondId] as Set
        secondReply[1].split(',') as Set == [firstId, secondId] as Set

        when:
        first.close()

        then:
        conditions.eventually {
            group.size() == 1
        }

        when:
        second.send('ids')

        then:
        second.replies.poll(10, TimeUnit.SECONDS) == "$secondId:$secondId"

        when:
        second.close()

        then:
        conditions.eventually {
            group.isEmpty()
        }

        cleanup:
        client.close()
        ctx.close()
    }

    @Requires(property = 'spec.name', value = 'OpenSessionsSpec')
    @ServerWebSocket('/open-sessions')
    static class ServerSocket {
        @OnMessage
        def onMessage(String message, WebSocketSession session) {
            return session.send(session.id + ':' + session.openSessions*.id.sort().join(','))
        }
    }

    @ClientWebSocket
    static abstract class ClientSocket implements AutoCloseable {
        final LinkedBlockingQueue<String> replies = new LinkedBlockingQueue<>()

        abstract void send(String message)

        @OnMessage
        void onMessage(String message) {
            replies.add(message)
        }
    }
}
