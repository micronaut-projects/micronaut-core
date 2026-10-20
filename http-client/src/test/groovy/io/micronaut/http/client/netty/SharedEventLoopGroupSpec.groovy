package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import spock.lang.Specification

class SharedEventLoopGroupSpec extends Specification {

    def 'a client does not shut down an event loop group it is given'() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'SharedEventLoopGroupSpec'])
        def group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
        def first = DefaultHttpClient.builder().uri(server.URI).eventLoopGroup(group).build()
        def second = DefaultHttpClient.builder().uri(server.URI).eventLoopGroup(group).build()

        expect:
        first.toBlocking().retrieve('/shared-group') == 'ok'

        when:
        first.close()

        then:
        !group.isShuttingDown()
        second.toBlocking().retrieve('/shared-group') == 'ok'

        cleanup:
        second?.close()
        group.shutdownGracefully(0, 1, java.util.concurrent.TimeUnit.SECONDS).await()
        server.close()
    }

    @Requires(property = 'spec.name', value = 'SharedEventLoopGroupSpec')
    @Controller('/shared-group')
    static class SharedGroupController {
        @Get
        String index() {
            'ok'
        }
    }
}
