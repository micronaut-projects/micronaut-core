package io.micronaut.http.server.netty.errors

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Status
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class UnsupportedOperationExceptionHandlerSpec extends Specification {

    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'UnsupportedOperationExceptionHandlerSpec'
    ])

    void "UnsupportedOperationException returns 501 not implemented"() {
        when:
        HttpRequest<?> request = HttpRequest.GET('/unsupported')
        embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL).toBlocking().exchange(request)

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.NOT_IMPLEMENTED
    }

    @Requires(property = 'spec.name', value = 'UnsupportedOperationExceptionHandlerSpec')
    @Controller('/unsupported')
    static class UnsupportController {
        @Get
        @Status(HttpStatus.OK)
        void index() {
            throw new UnsupportedOperationException()
        }

    }
}
