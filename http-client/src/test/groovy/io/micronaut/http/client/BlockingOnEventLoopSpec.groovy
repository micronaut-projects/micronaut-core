package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class BlockingOnEventLoopSpec extends Specification {
    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'BlockingSpec'
    ])

    @Shared
    @AutoCleanup
    BlockingHttpClient client = server.applicationContext.createBean(HttpClient, server.URL).toBlocking()

    def 'blocking on any event loop should fail'() {
        when:
        String resp = client.retrieve("/block")

        then:
        resp == "HttpClientException"
    }

    @Controller
    @Requires(property = "spec.name", value = "BlockingSpec")
    static class Ctrl {
        @Inject
        HttpClient client

        @Get("/block")
        @Produces(MediaType.TEXT_PLAIN)
        def block() {
            try {
                client.toBlocking().retrieve("https://micronaut.io")
                return "passed"
            } catch (HttpClientException ignored) {
                return "HttpClientException"
            }
        }
    }

}
