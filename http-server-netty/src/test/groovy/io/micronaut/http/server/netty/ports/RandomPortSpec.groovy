package io.micronaut.http.server.netty.ports

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class RandomPortSpec extends Specification {

    void "test random port works with -1"() {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(
                EmbeddedServer,
                ['micronaut.server.port': -1]
        )

        then:
        noExceptionThrown()
        embeddedServer.port != -1

        cleanup:
        embeddedServer.close()
    }
}
