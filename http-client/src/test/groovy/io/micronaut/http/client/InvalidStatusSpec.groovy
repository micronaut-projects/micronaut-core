package io.micronaut.http.client


import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class InvalidStatusSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'spec.name': 'InvalidStatusSpec'
    ])

    void "test receiving an invalid status code"() {
        given:
        EmbeddedServer server = context.getBean(EmbeddedServer)
        server.start()
        StreamingHttpClient client = context.createBean(StreamingHttpClient, server.URL)

        when:
        def response = client.toBlocking().exchange("/invalid-status", String)

        then:
        response.code() == 290

        when:
        response.status()
        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "Invalid HTTP status code: 290"

        cleanup:
        client.close()
        server.stop()
    }

    @Controller('/invalid-status')
    @Requires(property = 'spec.name', value = 'InvalidStatusSpec')
    static class InvalidStatusController {
        @Get
        HttpResponse<?> status() {
            return HttpResponse.ok().status(290)
        }
    }
}
