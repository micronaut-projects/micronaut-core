package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Specification

class InvalidStatusSpec extends Specification {

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'InvalidStatusSpec',
            'micronaut.http.client.exception-on-error-status': false
    ])

    @AutoCleanup
    StreamingHttpClient client = server.applicationContext.createBean(StreamingHttpClient, server.URL)

    void "test receiving an invalid status code"(int status) {
        when:
        def response = client.toBlocking().exchange("/invalid-status/$status", String, String)

        then:
        response.code() == status

        when:
        response.status()
        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "Invalid HTTP status code: $status"

        where:
        status << [290, 700]
    }

    @Controller('/invalid-status')
    @Requires(property = 'spec.name', value = 'InvalidStatusSpec')
    static class InvalidStatusController {
        @Get("/290")
        HttpResponse<?> status290() {
            return HttpResponse.ok().status(290)
        }

        @Get("/700")
        HttpResponse<?> status700() {
            return HttpResponse.ok().status(700)
        }
    }
}
