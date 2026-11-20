package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * A path variable that does not decode, e.g. an overlong UTF-8 encoding of a dot, is a bad
 * request, not a failure of the server.
 */
class MalformedPathVariableSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'MalformedPathVariableSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "a malformed path variable is answered 400"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET(path), String)

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.BAD_REQUEST

        where:
        path << ['/malformed-variable/%C0%AE%C0%AE', '/malformed-variable/%FF']
    }

    void "a valid encoded path variable is decoded"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.GET('/malformed-variable/a%20b')) == 'a b'
    }

    @Requires(property = 'spec.name', value = 'MalformedPathVariableSpec')
    @Controller('/malformed-variable')
    static class VariableController {
        @Get('/{name}')
        String echo(String name) {
            name
        }
    }
}
