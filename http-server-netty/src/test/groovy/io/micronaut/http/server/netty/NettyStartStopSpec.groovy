package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.discovery.event.ServiceReadyEvent
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class NettyStartStopSpec extends Specification {

    void "stopping and starting the netty server in a named application should work"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'NettyStartStopSpec',
                'micronaut.application.name': 'example'
        ])
        def listener = server.applicationContext.getBean(StartListener)

        when:
        server.stop()

        and:
        server.start()

        then:
        listener.eventCount.get() == 2
        server.applicationContext.isRunning()

        cleanup:
        server.stop()
    }

    @Context
    @Singleton
    @Requires(property = "spec.name", value = "NettyStartStopSpec")
    static class StartListener implements ApplicationEventListener<ServiceReadyEvent> {

        AtomicInteger eventCount = new AtomicInteger(0)

        @Override
        void onApplicationEvent(ServiceReadyEvent event) {
            eventCount.incrementAndGet()
        }
    }
}
