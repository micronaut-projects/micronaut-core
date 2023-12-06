package io.micronaut.http.server.netty.context

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class ContextURISpec extends Specification {

    void "test getContextURI returns the URI with the context path when context path is set"() {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.context-path': '/context',
                'micronaut.server.port': 60006
        ])

        then:
        embeddedServer.getContextURI().toString() == 'http://localhost:60006/context'

        cleanup:
        embeddedServer.close()
    }

    void "test getContextURI returns the base URI when context path is not set"() {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.port': 60006
        ])

        then:
        embeddedServer.getContextURI().toString() == 'http://localhost:60006'

        cleanup:
        embeddedServer.close()
    }

    void "test getContextURI returns the base URI when context path is set to an empty string"() {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.context-path': '',
                'micronaut.server.port': 60006
        ])

        then:
        embeddedServer.getContextURI().toString() == 'http://localhost:60006'

        cleanup:
        embeddedServer.close()
    }
}
