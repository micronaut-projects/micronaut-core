package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.ShutdownEvent
import io.micronaut.http.server.exceptions.ServerStartupException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.runtime.server.event.ServerShutdownEvent
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The server stops the application context when it stops (or fails to bind), and stopping the
 * application context stops the server. When both happen at the same time on different threads,
 * neither may wait for the other's lock while holding its own.
 */
class NettyStopDeadlockSpec extends Specification {

    private static final long TIMEOUT_SECONDS = 20

    void cleanup() {
        ServerShutdownHook.action = null
        ContextShutdownHook.latch = null
        ContextShutdownHook.action = null
    }

    void 'a bind failure does not deadlock with a concurrent application context stop'() {
        given: 'the port is taken by a dual-stack listener'
        ServerSocket occupied = new ServerSocket(0)
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name'            : 'NettyStopDeadlockSpec',
                'micronaut.server.port': occupied.localPort
        ])
        NettyHttpServer server = (NettyHttpServer) ctx.getBean(EmbeddedServer)

        when: 'the context is stopped while the failed server is shutting down'
        def result = raceWithContextStop(ctx) { server.start() }

        then: 'both finish, the start failure is reported and the context is stopped'
        result.serverThread.alive == false
        result.contextThread.alive == false
        result.serverFailure.get() instanceof ServerStartupException
        result.contextFailure.get() == null
        !ctx.running
        !server.running

        cleanup:
        occupied.close()
        stopUnlessDeadlocked(ctx, result)
    }

    void 'stopping the server does not deadlock with a concurrent application context stop'() {
        given:
        NettyHttpServer server = (NettyHttpServer) ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'NettyStopDeadlockSpec'
        ])
        ApplicationContext ctx = server.applicationContext

        when: 'the context is stopped while the server is stopping'
        def result = raceWithContextStop(ctx) { server.stop() }

        then:
        result.serverThread.alive == false
        result.contextThread.alive == false
        result.serverFailure.get() == null
        result.contextFailure.get() == null
        !ctx.running
        !server.running

        cleanup:
        stopUnlessDeadlocked(ctx, result)
    }

    void 'a start racing with a stop is not undone by the application context stop'() {
        given:
        NettyHttpServer server = (NettyHttpServer) ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'NettyStopDeadlockSpec'
        ])
        ApplicationContext ctx = server.applicationContext
        AtomicReference<Throwable> startFailure = new AtomicReference<>()
        Thread startThread = new Thread({
            try {
                server.start()
            } catch (Throwable t) {
                startFailure.set(t)
            }
        }, 'server-start')
        startThread.daemon = true
        ContextShutdownHook.action = {
            ContextShutdownHook.action = null
            // the server is stopping the application context: start the server again right now
            startThread.start()
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (startThread.alive && startThread.state != Thread.State.WAITING && System.nanoTime() < deadline) {
                Thread.sleep(10)
            }
        }

        when:
        server.stop()
        startThread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS))

        then: 'the start happens after the stop and is not undone by it'
        !startThread.alive
        startFailure.get() == null
        server.running
        ctx.running

        cleanup:
        if (!startThread.alive) {
            server.stop()
        }
    }

    private static void stopUnlessDeadlocked(ApplicationContext ctx, Map result) {
        // a deadlocked context thread still holds the context's lock, stopping it here would hang too
        if (result == null || !((Thread) result.contextThread).alive) {
            ctx.stop()
        }
    }

    /**
     * Run the given server action on one thread and, from inside the server's shutdown (while the
     * server action is still in progress), stop the application context on another thread. The
     * server thread only continues once the context thread is inside the context's stop.
     */
    private static Map raceWithContextStop(ApplicationContext ctx, Closure<?> serverAction) {
        AtomicReference<Throwable> serverFailure = new AtomicReference<>()
        AtomicReference<Throwable> contextFailure = new AtomicReference<>()
        CountDownLatch contextStopping = new CountDownLatch(1)
        Thread contextThread = new Thread({
            try {
                ctx.stop()
            } catch (Throwable t) {
                contextFailure.set(t)
            }
        }, 'context-stop')
        contextThread.daemon = true
        ContextShutdownHook.latch = contextStopping
        ServerShutdownHook.action = {
            ServerShutdownHook.action = null
            contextThread.start()
            // the context thread now holds the context's lock and is about to stop the server
            assert contextStopping.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        Thread serverThread = new Thread({
            try {
                serverAction.call()
            } catch (Throwable t) {
                serverFailure.set(t)
            }
        }, 'server-action')
        serverThread.daemon = true
        serverThread.start()

        serverThread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS))
        contextThread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS))
        return [serverThread: serverThread, contextThread: contextThread, serverFailure: serverFailure, contextFailure: contextFailure]
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'NettyStopDeadlockSpec')
    static class ServerShutdownHook implements ApplicationEventListener<ServerShutdownEvent> {
        static volatile Runnable action

        @Override
        void onApplicationEvent(ServerShutdownEvent event) {
            Runnable a = action
            if (a != null) {
                a.run()
            }
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'NettyStopDeadlockSpec')
    static class ContextShutdownHook implements ApplicationEventListener<ShutdownEvent> {
        static volatile CountDownLatch latch
        static volatile Runnable action

        @Override
        void onApplicationEvent(ShutdownEvent event) {
            latch?.countDown()
            Runnable a = action
            if (a != null) {
                a.run()
            }
        }
    }
}
