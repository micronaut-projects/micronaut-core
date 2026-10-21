package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class SharedEventLoopGroupSpec extends Specification {

    def 'a client runs on the event loop of another client, which keeps owning it'() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'SharedEventLoopGroupSpec'])
        def owner = DefaultHttpClient.builder().uri(server.URI).build()
        def sharing = DefaultHttpClient.builder().uri(server.URI).eventLoopOf(owner).build()
        def group = owner.connectionManager().eventLoopGroup()

        expect:
        sharing.connectionManager().eventLoopGroup().is(group)
        sharing.toBlocking().retrieve('/shared-group') == 'ok'

        when:
        sharing.close()

        then:
        !group.isShuttingDown()
        owner.toBlocking().retrieve('/shared-group') == 'ok'

        when:
        owner.close()

        then:
        group.isShuttingDown()

        cleanup:
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
