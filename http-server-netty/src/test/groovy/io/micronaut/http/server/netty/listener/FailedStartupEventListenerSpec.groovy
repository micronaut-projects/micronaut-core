package io.micronaut.http.server.netty.listener

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.runtime.server.event.ServerStartupEvent
import jakarta.inject.Singleton
import spock.lang.Specification

/**
 * a failing ServerStartupEvent listener leaves the listeners bound and serving.
 */
class FailedStartupEventListenerSpec extends Specification {

    static volatile int boundPort

    void 'a failed start leaves no listener serving'() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['spec.name': 'FailedStartupEventListenerSpec', 'micronaut.server.port': -1])
        EmbeddedServer server = ctx.getBean(EmbeddedServer)

        when:
        server.start()

        then:
        thrown(RuntimeException)

        when:
        int port = boundPort
        String afterFailedStart = ListenerRawHttp.tryGet(port, '/failed-start')
        server.stop()
        String afterServerStop = ListenerRawHttp.tryGet(port, '/failed-start')
        ctx.close()
        String afterContextClose = ListenerRawHttp.tryGet(port, '/failed-start')

        then: 'nothing is served after the failed start, and stopping closes the listener'
        [afterFailedStart, afterServerStop, afterContextClose] == [null, null, null]

        cleanup:
        ctx.close()
    }

    @Controller('/failed-start')
    @Requires(property = 'spec.name', value = 'FailedStartupEventListenerSpec')
    static class AppController {
        @Get
        String get() { 'app-route' }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'FailedStartupEventListenerSpec')
    static class FailingListener implements ApplicationEventListener<ServerStartupEvent> {
        @Override
        void onApplicationEvent(ServerStartupEvent event) {
            boundPort = event.source.port
            throw new IllegalStateException('startup wiring failed')
        }
    }
}
