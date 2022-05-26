package io.micronaut.docs.datavalidation.pogo

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
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
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    //tag::pojovalidated[]
    def "invoking /email/send parse parameters in a POJO and validates"() {
        when:
        Email email = new Email(subject: 'Hi', recipient: '')
        client.toBlocking().exchange(HttpRequest.POST('/email/send', email))

        then:
        def e = thrown(HttpClientResponseException)
        def response = e.response
        response.status == HttpStatus.BAD_REQUEST

        when:
        email = new Email(subject: 'Hi', recipient: 'me@micronaut.example')
        response = client.toBlocking().exchange(HttpRequest.POST('/email/send', email))

        then:
        response.status == HttpStatus.OK
    }
    //end::pojovalidated[]
}
