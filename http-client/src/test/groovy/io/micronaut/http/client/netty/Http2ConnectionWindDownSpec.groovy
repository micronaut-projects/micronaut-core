package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.server.netty.NettyServerCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.Channel
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A refresh winds a pooled HTTP/2 connection down: a response that is still running on it is
 * answered on it, a later request goes to a new connection, and the old connection closes once
 * its response is done.
 */
class Http2ConnectionWindDownSpec extends Specification {

    def 'a refresh keeps a running h2c response on its connection and closes the connection after it (max connections #maxConnections)'() {
        given:
        ApplicationContext serverContext = ApplicationContext.run([
                'spec.name'                    : 'Http2ConnectionWindDownSpec',
                'micronaut.server.port'        : -1,
                'micronaut.server.http-version': '2.0',
        ])
        URL url = serverContext.getBean(EmbeddedServer).start().getURL()
        Connections connections = serverContext.getBean(Connections)
        HoldController controller = serverContext.getBean(HoldController)
        Map<String, Object> clientProperties = [
                'micronaut.http.client.plaintext-mode': 'h2c_prior_knowledge',
                'micronaut.http.client.read-timeout'  : '10s',
        ]
        if (maxConnections != null) {
            clientProperties['micronaut.http.client.pool.max-concurrent-http2-connections'] = maxConnections
        }
        ApplicationContext clientContext = ApplicationContext.run(clientProperties)
        HttpClient client = clientContext.createBean(HttpClient, url)
        int pooled = client.toBlocking().retrieve('/h2-wind-down/port') as int

        when: 'a response is held open on the pooled connection'
        CompletableFuture<String> held = Mono.from(client.retrieve('/h2-wind-down/hold')).toFuture()

        then:
        controller.received.await(10, TimeUnit.SECONDS)
        controller.heldPort == pooled

        when: 'another thread refreshes the client while the response is held'
        Thread refresher = Thread.start { client.refresh() }
        refresher.join(10_000)
        // the connection that winds down must not count against the connection limit: with the
        // default of one, this request would otherwise wait until the held response is done
        CompletableFuture<String> nextResponse = Mono.from(client.retrieve('/h2-wind-down/port')).toFuture()
        int next = nextResponse.get(5, TimeUnit.SECONDS) as int

        then: 'a new request goes to a new connection, and the old one stays open for the held response'
        !refresher.alive
        next != pooled
        connections.byPort[pooled].isActive()
        !held.done

        when: 'the held response is answered'
        controller.answer.tryEmitValue('held')

        then: 'it arrives on the old connection, which then closes'
        held.get(10, TimeUnit.SECONDS) == 'held'
        new PollingConditions(timeout: 10).eventually {
            assert !connections.byPort[pooled].isActive()
        }
        connections.byPort[next].isActive()

        cleanup:
        clientContext?.close()
        serverContext?.close()

        where:
        maxConnections << [null, 1, 2]
    }

    /**
     * The connection channels the server accepted, by the client's port.
     */
    @Requires(property = 'spec.name', value = 'Http2ConnectionWindDownSpec')
    @Singleton
    static class Connections implements BeanCreatedEventListener<NettyServerCustomizer.Registry> {
        final Map<Integer, Channel> byPort = new ConcurrentHashMap<>()

        @Override
        NettyServerCustomizer.Registry onCreated(BeanCreatedEvent<NettyServerCustomizer.Registry> event) {
            event.bean.register(new NettyServerCustomizer() {
                @Override
                NettyServerCustomizer specializeForChannel(Channel channel, NettyServerCustomizer.ChannelRole role) {
                    if (role == NettyServerCustomizer.ChannelRole.CONNECTION) {
                        byPort.put(((InetSocketAddress) channel.remoteAddress()).port, channel)
                    }
                    return this
                }
            })
            return event.bean
        }
    }

    @Requires(property = 'spec.name', value = 'Http2ConnectionWindDownSpec')
    @Controller('/h2-wind-down')
    static class HoldController {
        final CountDownLatch received = new CountDownLatch(1)
        final Sinks.One<String> answer = Sinks.one()
        volatile int heldPort

        @Get(value = '/port', produces = 'text/plain')
        String port(HttpRequest<?> request) {
            request.remoteAddress.port as String
        }

        @Get(value = '/hold', produces = 'text/plain')
        Mono<String> hold(HttpRequest<?> request) {
            heldPort = request.remoteAddress.port
            received.countDown()
            answer.asMono()
        }
    }
}
