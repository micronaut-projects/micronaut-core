package io.micronaut.http.client.httpclientexceptionbody

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientException
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

    def "verify after an HttpClientException the response body can be bound to a POJO"() {

        when:
        client.toBlocking().exchange(HttpRequest.GET("/books"))

        then:
        def e = thrown(HttpClientException)
        e.response.status == HttpStatus.UNAUTHORIZED

        when:
        Optional<CustomError> jsonError = e.response.getBody(CustomError)

        then:
        jsonError.isPresent()
        jsonError.get().status == 401
        jsonError.get().error == 'Unauthorized'
        jsonError.get().message == 'No message available'
        jsonError.get().path == '/api/announcements'
    }
}
