package io.micronaut.docs.httpclientexceptionbody

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class BindHttpClientExceptionBodySpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': BindHttpClientExceptionBodySpec.simpleName], Environment.TEST)

    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.getApplicationContext().createBean(HttpClient.class, embeddedServer.getURL())

    //tag::test[]
    def "after an HttpClientException the response body can be bound to a POJO"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("/books/1680502395"),
                Argument.of(Book), // <1>
                Argument.of(CustomError)) // <2>

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.UNAUTHORIZED

        when:
        Optional<CustomError> jsonError = e.response.getBody(CustomError)

        then:
        jsonError.isPresent()
        jsonError.get().status == 401
        jsonError.get().error == 'Unauthorized'
        jsonError.get().message == 'No message available'
        jsonError.get().path == '/books/1680502395'
    }
    //end::test[]

    def "test exception binding error response"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("/books/1680502395"),
                Argument.of(Book), // <1>
                Argument.of(OtherError)) // <2>

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.UNAUTHORIZED

        when:
        Optional<OtherError> jsonError = e.response.getBody(OtherError)

        then:
        noExceptionThrown()
        !jsonError.isPresent()
    }

    def "verify bind error is thrown"() {
        when:
        HttpResponse rsp = client.toBlocking().exchange(HttpRequest.GET("/books/1491950358"),
                Argument.of(Book),
                Argument.of(CustomError))

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.OK
        e.message.startsWith("Error decoding HTTP response body")
        e.message.contains('cannot deserialize from Object value') // the jackson error
    }
}