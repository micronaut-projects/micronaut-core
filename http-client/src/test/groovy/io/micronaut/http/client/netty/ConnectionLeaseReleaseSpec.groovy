package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class ConnectionLeaseReleaseSpec extends Specification {

    def 'a request whose body cannot be encoded gives its connection back'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                                 : 'ConnectionLeaseReleaseSpec',
                'micronaut.http.client.pool.max-concurrent-http1-connections': 1,
                'micronaut.http.client.pool.acquire-timeout'                : '3s',
                'micronaut.http.client.read-timeout'                        : '60s',
        ])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

        when: 'the only connection is open and idle'
        def first = client.toBlocking().retrieve('/lease/hello')
        then:
        first == 'hello'

        when: 'no writer encodes this body: the request fails after its connection was taken'
        client.toBlocking().exchange(HttpRequest.POST('/lease/hello', new Unencodable()).contentType('application/x-lease-unknown'))
        then:
        thrown(RuntimeException)

        when: 'the connection is free for the next request'
        def second = client.toBlocking().retrieve('/lease/hello')
        then:
        second == 'hello'

        cleanup:
        client.close()
        server.close()
    }

    static class Unencodable {
    }

    @Controller('/lease')
    @Requires(property = 'spec.name', value = 'ConnectionLeaseReleaseSpec')
    static class LeaseController {
        @Get('/hello')
        String hello() {
            'hello'
        }
    }
}
