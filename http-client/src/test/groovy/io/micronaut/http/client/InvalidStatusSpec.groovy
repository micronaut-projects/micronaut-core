package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class InvalidStatusSpec extends Specification {

    void "test receiving an invalid status code"(int status) {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': 'InvalidStatusSpec',
                'micronaut.http.client.exception-on-error-status': false
        ])
        EmbeddedServer server = context.getBean(EmbeddedServer)
        server.start()
        StreamingHttpClient client = context.createBean(StreamingHttpClient, server.URL)

        when:
        def response = client.toBlocking().exchange("/invalid-status/$status", String, String)

        then:
        response.code() == status

        when:
        response.status()
        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "Invalid HTTP status code: $status"

        cleanup:
        client.close()
        server.stop()
        context.close()

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
