package io.micronaut.docs.datavalidation.pogo

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class EmailControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ['spec.name': 'datavalidationpogo'],
            "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient, embeddedServer.getURL())

    //tag::pojovalidated[]
    def "invoking /email/send parse parameters in a POJO and validates"() {
        when:
        Email email = new Email()
        email.subject = 'Hi'
        email.recipient = ''
        client.toBlocking().exchange(HttpRequest.POST('/email/send', email))

        then:
        def e = thrown(HttpClientResponseException)

        when:
        def response = e.response

        then:
        response.status == HttpStatus.BAD_REQUEST

        when:
        email = new Email()
        email.subject = 'Hi'
        email.recipient = 'me@micronaut.example'
        client.toBlocking().exchange(HttpRequest.POST('/email/send', email))

        then:
        noExceptionThrown()
    }
    //end::pojovalidated[]
}
