package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.netty.websocket.NettyWebSocketSession
import io.micronaut.http.netty.websocket.WebSocketSessionRepository
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.channel.Channel
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.group.DefaultChannelGroup
import io.netty.util.concurrent.GlobalEventExecutor
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
        def repository = (WebSocketSessionRepository) server
        def client = ctx.createBean(WebSocketClient, server.URI)
        def conditions = new PollingConditions(timeout: 10)

        when:
        def first = Mono.from(client.connect(ClientSocket, '/open-sessions')).block()
        def second = Mono.from(client.connect(ClientSocket, '/open-sessions')).block()

        then:
        conditions.eventually {
            repository.openSessions.size() == 2
            repository.channelGroup.size() == 2
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
            repository.openSessions*.id == [secondId]
            repository.channelGroup.size() == 1
        }

        when:
        second.send('ids')

        then:
        second.replies.poll(10, TimeUnit.SECONDS) == "$secondId:$secondId"

        when:
        second.close()

        then:
        conditions.eventually {
            repository.openSessions.isEmpty()
            repository.channelGroup.isEmpty()
        }

        cleanup:
        client.close()
        ctx.close()
    }

    def "sessions are forgotten when the channel closes without removeChannel"() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'OpenSessionsSpec'])
        def repository = (WebSocketSessionRepository) ctx.getBean(EmbeddedServer)
        def channel = new EmbeddedChannel()
        def session = Mock(NettyWebSocketSession)
        channel.attr(NettyWebSocketSession.WEB_SOCKET_SESSION_KEY).set(session)
        // always reports open, so that only the bookkeeping decides whether it is returned
        session.isOpen() >> true

        when:
        repository.addChannel(channel)

        then:
        repository.openSessions == [session] as Set

        when:
        channel.close().sync()

        then:
        repository.openSessions.isEmpty()
        repository.channelGroup.isEmpty()

        cleanup:
        ctx.close()
    }

    def "a channel removed from the group directly is not reported"() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'OpenSessionsSpec'])
        def repository = (WebSocketSessionRepository) ctx.getBean(EmbeddedServer)
        def channel = new EmbeddedChannel()
        def session = Mock(NettyWebSocketSession)
        channel.attr(NettyWebSocketSession.WEB_SOCKET_SESSION_KEY).set(session)
        session.isOpen() >> true
        repository.addChannel(channel)

        when:
        repository.channelGroup.remove(channel)

        then:
        repository.openSessions.isEmpty()

        cleanup:
        channel.close()
        ctx.close()
    }

    def "default implementation derives the sessions from the channel group"() {
        given:
        def group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
        def repository = new WebSocketSessionRepository() {
            @Override
            void addChannel(Channel channel) {
                group.add(channel)
            }

            @Override
            void removeChannel(Channel channel) {
                group.remove(channel)
            }

            @Override
            io.netty.channel.group.ChannelGroup getChannelGroup() {
                return group
            }
        }
        def openChannel = new EmbeddedChannel()
        def openSession = Mock(NettyWebSocketSession)
        openSession.isOpen() >> true
        openChannel.attr(NettyWebSocketSession.WEB_SOCKET_SESSION_KEY).set(openSession)
        def closedChannel = new EmbeddedChannel()
        def closedSession = Mock(NettyWebSocketSession)
        closedSession.isOpen() >> false
        closedChannel.attr(NettyWebSocketSession.WEB_SOCKET_SESSION_KEY).set(closedSession)
        def noSessionChannel = new EmbeddedChannel()

        when:
        repository.addChannel(openChannel)
        repository.addChannel(closedChannel)
        repository.addChannel(noSessionChannel)

        then:
        repository.openSessions == [openSession] as Set

        cleanup:
        openChannel.close()
        closedChannel.close()
        noSessionChannel.close()
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
