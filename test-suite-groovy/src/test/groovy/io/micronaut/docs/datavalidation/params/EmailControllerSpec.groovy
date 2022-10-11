package io.micronaut.docs.datavalidation.params

import io.micronaut.context.ApplicationContext
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
            ['spec.name': 'datavalidationparams'],
            "test")

    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    //tag::paramsvalidated[]
    def "test parameter validation"() {
        when:
        client.toBlocking().exchange('/email/send?subject=Hi&recipient=')

        then:
        def e = thrown(HttpClientResponseException)
        def response = e.response
        response.status == HttpStatus.BAD_REQUEST

        when:
        response = client.toBlocking().exchange('/email/send?subject=Hi&recipient=me@micronaut.example')

        then:
        response.status == HttpStatus.OK
    }
    //end::paramsvalidated[]
}
