package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class NettyStopSpec extends Specification {

    def 'can shutdown netty and application context'() {
        NettyEmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'NettyStopSpec'])
        def ctx = server.applicationContext

        when:
        server.stop()

        then:
        !ctx.running
    }

    def 'can shutdown netty and keep application context running'() {
        NettyEmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'NettyStopSpec'])
        def ctx = server.applicationContext

        when:
        server.stopServerOnly()

        then:
        ctx.running

        cleanup:
        ctx.stop()
    }
}
