package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Internal
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration
import io.micronaut.http.server.exceptions.ServerStartupException
import io.micronaut.http.server.netty.NettyServerCustomizer.ChannelRole
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
import io.micronaut.runtime.server.event.ServerShutdownEvent
import io.micronaut.runtime.server.event.ServerStartupEvent
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.EventLoopGroup
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NettyRunStateSpec extends Specification {
    ApplicationContext context = ApplicationContext.run([
            'spec.name': 'NettyRunStateSpec',
            'micronaut.server.port': -1,
            'micronaut.server.netty.parent.shutdown-quiet-period': '0s',
            'micronaut.server.netty.worker.event-loop-group': 'run-state-worker',
            'micronaut.server.netty.worker.threads': 1
    ])
    TrackingServer server = new TrackingServer(context)

    void cleanup() {
        context.getBean(ShutdownListener).action = null
        context.getBean(StartupListener).action = null
        server.stopServerOnly()
        context.close()
    }

    void 'concurrent starts and stops close every detached run'() {
        given:
        def executor = Executors.newFixedThreadPool(4)
        CountDownLatch go = new CountDownLatch(1)

        when:
        def tasks = (0..<4).collect {
            executor.submit({
                assert go.await(10, TimeUnit.SECONDS)
                3.times {
                    server.start()
                    server.stopServerOnly()
                }
            } as Runnable)
        }
        go.countDown()
        tasks.each { it.get(30, TimeUnit.SECONDS) }
        server.stopServerOnly()

        then:
        !server.running
        server.boundPorts.empty
        server.parentGroup == null
        server.channels.every { !it.open }
        server.groups.every { it.shuttingDown }

        cleanup:
        go.countDown()
        executor.shutdownNow()
    }

    void 'an old stop cannot clear the state published by a new start'() {
        given:
        server.start()
        EventLoopGroup oldParent = server.parentGroup
        CountDownLatch stopping = new CountDownLatch(1)
        CountDownLatch finishStop = new CountDownLatch(1)
        context.getBean(ShutdownListener).action = {
            stopping.countDown()
            assert finishStop.await(10, TimeUnit.SECONDS)
        }
        def executor = Executors.newSingleThreadExecutor()

        when:
        def stop = executor.submit({ server.stopServerOnly() } as Runnable)
        assert stopping.await(10, TimeUnit.SECONDS)
        server.start()
        int newPort = server.port
        EventLoopGroup newParent = server.parentGroup
        finishStop.countDown()
        stop.get(10, TimeUnit.SECONDS)

        then:
        server.running
        server.boundPorts == [newPort] as Set
        server.parentGroup.is(newParent)
        !newParent.is(oldParent)
        oldParent.shuttingDown
        !newParent.shuttingDown
        new Socket('127.0.0.1', newPort).withCloseable { it.connected }

        cleanup:
        finishStop.countDown()
        executor.shutdownNow()
    }

    void 'a stop racing an in-progress start returns without detaching that run'() {
        given:
        CountDownLatch starting = new CountDownLatch(1)
        CountDownLatch finishStart = new CountDownLatch(1)
        server.beforeBootstrap = {
            starting.countDown()
            assert finishStart.await(10, TimeUnit.SECONDS)
        }
        def executor = Executors.newFixedThreadPool(2)

        when:
        def start = executor.submit({ server.start() } as Runnable)
        assert starting.await(10, TimeUnit.SECONDS)
        def stop = executor.submit({
            server.stopServerOnly()
        } as Runnable)
        stop.get(5, TimeUnit.SECONDS)
        finishStart.countDown()
        start.get(10, TimeUnit.SECONDS)

        then:
        server.running
        !server.boundPorts.empty
        server.channels.every { it.open }
        server.groups.every { !it.shuttingDown }

        cleanup:
        finishStart.countDown()
        executor.shutdownNow()
    }

    void 'a startup listener can wait for another thread to stopServerOnly'() {
        given:
        def executor = Executors.newSingleThreadExecutor()
        context.getBean(StartupListener).action = {
            executor.submit({ server.stopServerOnly() } as Runnable).get(3, TimeUnit.SECONDS)
        }

        when:
        server.start()

        then:
        server.running
        server.channels.every { it.open }

        cleanup:
        executor.shutdownNow()
    }

    void 'a failure before binding at #stage releases groups without a shutdown event'() {
        given:
        def events = []
        context.getBean(ShutdownListener).action = { events.add('shutdown') }
        Runnable fail = { throw new IllegalStateException('startup hook failed') }
        if (stage == 'parent') {
            server.beforeParent = fail
        } else {
            server.beforeBootstrap = fail
        }

        when:
        server.start()

        then:
        thrown(RuntimeException)
        !server.running
        server.boundPorts.empty
        !server.groups.empty
        server.groups.every { it.shuttingDown }
        events.empty

        where:
        stage << ['parent', 'bootstrap']
    }

    void 'a later bind failure releases earlier listeners and both owned groups'() {
        given:
        ServerSocket occupied = new ServerSocket(0, 50, InetAddress.loopbackAddress)
        ServerSocket available = new ServerSocket(0, 50, InetAddress.loopbackAddress)
        int port = available.localPort
        available.close()
        server.serverConfiguration.listeners = [
                NettyHttpServerConfiguration.NettyListenerConfiguration.createTcp('localhost', port, false, null, null),
                NettyHttpServerConfiguration.NettyListenerConfiguration.createTcp('localhost', occupied.localPort, false, null, null)
        ]
        // Listener configurations are captured by the server constructor.
        server = new TrackingServer(context)

        when:
        server.start()

        then:
        def failure = thrown(ServerStartupException)
        failure.message == "Unable to start Micronaut server on localhost:${occupied.localPort}"
        failure.cause instanceof BindException
        !server.running
        server.boundPorts.empty
        server.channels.size() == 2
        server.channels.every { !it.open }
        server.groups.size() == 2
        server.groups.every { it.shuttingDown }
        new ServerSocket(port, 50, InetAddress.loopbackAddress).withCloseable { true }

        cleanup:
        occupied.close()
    }

    static class TrackingServer extends NettyHttpServer {
        final List<EventLoopGroup> groups = new CopyOnWriteArrayList<>()
        final List<Channel> channels = new CopyOnWriteArrayList<>()
        Runnable beforeBootstrap
        Runnable beforeParent

        TrackingServer(ApplicationContext context) {
            super(context.getBean(NettyHttpServerConfiguration), context.getBean(DefaultNettyEmbeddedServerFactory), false)
            register(new NettyServerCustomizer() {
                @Override
                NettyServerCustomizer specializeForChannel(Channel channel, ChannelRole role) {
                    if (role == ChannelRole.LISTENER) {
                        channels.add(channel)
                    }
                    return this
                }
            })
        }

        @Override
        protected EventLoopGroup createWorkerEventLoopGroup(EventLoopGroupConfiguration configuration) {
            def group = super.createWorkerEventLoopGroup(configuration)
            groups.add(group)
            return group
        }

        @Override
        protected EventLoopGroup createParentEventLoopGroup() {
            beforeParent?.run()
            def group = super.createParentEventLoopGroup()
            groups.add(group)
            return group
        }

        @Override
        protected ServerBootstrap createServerBootstrap() {
            beforeBootstrap?.run()
            return super.createServerBootstrap()
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'NettyRunStateSpec')
    @Internal
    static class StartupListener implements ApplicationEventListener<ServerStartupEvent> {
        volatile Runnable action

        @Override
        void onApplicationEvent(ServerStartupEvent event) {
            action?.run()
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'NettyRunStateSpec')
    static class ShutdownListener implements ApplicationEventListener<ServerShutdownEvent> {
        volatile Runnable action

        @Override
        void onApplicationEvent(ServerShutdownEvent event) {
            action?.run()
        }
    }
}
