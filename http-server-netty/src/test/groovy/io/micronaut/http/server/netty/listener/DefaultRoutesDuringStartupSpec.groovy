package io.micronaut.http.server.netty.listener

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.server.netty.NettyServerCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.Channel
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * default routes are served on an expose-default-routes=false listener before
 * applyDefaultPorts runs.
 */
class DefaultRoutesDuringStartupSpec extends Specification {

    static final List<String> PROBES = new CopyOnWriteArrayList<>()
    static volatile int mgmtPort
    static volatile int otherMgmtPort

    void 'restricted listener never serves a default route, even during startup'() {
        given:
        PROBES.clear()
        mgmtPort = freePort()
        otherMgmtPort = freePort()
        int mainPort = freePort()
        ApplicationContext ctx = ApplicationContext.run([
                'spec.name'                                                    : 'DefaultRoutesDuringStartupSpec',
                'startup.mgmt-port'                                                : mgmtPort,
                'micronaut.server.netty.parent.threads'                        : 4,
                'micronaut.server.netty.listeners.a-mgmt.host'                 : '127.0.0.1',
                'micronaut.server.netty.listeners.a-mgmt.port'                 : mgmtPort,
                'micronaut.server.netty.listeners.a-mgmt.expose-default-routes': false,
                'micronaut.server.netty.listeners.b-main.host'                 : '127.0.0.1',
                'micronaut.server.netty.listeners.b-main.port'                 : mainPort,
                'micronaut.server.netty.listeners.c-mgmt.host'                 : '127.0.0.1',
                'micronaut.server.netty.listeners.c-mgmt.port'                 : otherMgmtPort,
                'micronaut.server.netty.listeners.c-mgmt.expose-default-routes': false,
        ])
        EmbeddedServer server = ctx.getBean(EmbeddedServer)

        when:
        server.start()

        then: 'after start the restricted listener hides the default route'
        ListenerRawHttp.tryGet(mgmtPort, '/startup-app').startsWith('HTTP/1.1 404')
        ListenerRawHttp.tryGet(mainPort, '/startup-app').contains('app-route')

        and: 'a probe made while the last listener was being bound found a restricted listener live (the listeners are bound in no fixed order, so two of the three are restricted)'
        PROBES.any { it != null }

        and: 'but it must not have served the default route there'
        PROBES.findAll { it != null }.every { !it.contains('app-route') }

        cleanup:
        ctx.close()
    }

    static int freePort() {
        new ServerSocket(0).withCloseable { it.localPort }
    }

    @Controller('/startup-app')
    @Requires(property = 'spec.name', value = 'DefaultRoutesDuringStartupSpec')
    static class AppController {
        @Get
        String get() { 'app-route' }
    }

    @Controller(value = '/startup-mgmt', port = '${startup.mgmt-port}')
    @Requires(property = 'spec.name', value = 'DefaultRoutesDuringStartupSpec')
    static class MgmtController {
        @Get
        String get() { 'mgmt-route' }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'DefaultRoutesDuringStartupSpec')
    static class WindowWidener implements BeanCreatedEventListener<NettyServerCustomizer.Registry>, NettyServerCustomizer {
        final AtomicInteger listeners = new AtomicInteger()

        @Override
        NettyServerCustomizer.Registry onCreated(BeanCreatedEvent<NettyServerCustomizer.Registry> event) {
            event.bean.register(this)
            return event.bean
        }

        @Override
        NettyServerCustomizer specializeForChannel(Channel channel, ChannelRole role) {
            if (role == ChannelRole.LISTENER && listeners.incrementAndGet() == 3) {
                // the first listener is bound, the second is being initialized: probe from another thread
                Thread probe = new Thread({
                    PROBES.add(ListenerRawHttp.tryGet(mgmtPort, '/startup-app'))
                    PROBES.add(ListenerRawHttp.tryGet(otherMgmtPort, '/startup-app'))
                })
                probe.start()
                probe.join(5000)
            }
            return this
        }
    }
}
