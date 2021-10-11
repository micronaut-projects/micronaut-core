package io.micronaut.http.server.netty.cors

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.http.HttpHeaders.*

class CorsSingleHeaderSpec extends Specification {

    Map<String, Object> getProperties(boolean singleHeader) {
        ['micronaut.server.cors.enabled': true,
         'micronaut.server.cors.single-header': singleHeader,
         'micronaut.server.cors.configurations.foo.allowedOrigins': ['foo.com'],
         'micronaut.server.cors.configurations.foo.allowedMethods': ['GET'],
         'micronaut.server.cors.configurations.foo.maxAge': -1,
         'micronaut.server.cors.configurations.foo.exposedHeaders': ['Content-Type'],
         'spec.name':'NettyCorsSpec'
        ]
    }

    @Unroll
    void 'test CORS single header config works where single-header=#singleHeader'() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, getProperties(singleHeader))
        HttpClient client = embeddedServer.getApplicationContext().createBean(HttpClient, embeddedServer.getURL())

        HttpResponse optionsResponse = Flux.from(client.exchange(
                HttpRequest.OPTIONS('/test/arbitrary')
                        .header(ORIGIN, 'foo.com')
                        .header(ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET)
                        .header(ACCESS_CONTROL_REQUEST_HEADERS, "${HttpHeaders.CONTENT_TYPE},${HttpHeaders.ACCEPT}")
        )).blockFirst()
        Set<String> optionsHeaderNames = optionsResponse.headers.names()

        HttpResponse response = Flux.from(client.exchange(
                HttpRequest.GET('/test/arbitrary')
                        .header(ORIGIN, 'foo.com')
        )).blockFirst()

        expect:
        optionsResponse.status == HttpStatus.OK
        optionsResponse.header(ACCESS_CONTROL_ALLOW_ORIGIN) == 'foo.com'
        optionsResponse.header(VARY) == ORIGIN
        optionsHeaderNames.contains(ACCESS_CONTROL_ALLOW_HEADERS)
        optionsHeaderNames.contains(ACCESS_CONTROL_ALLOW_METHODS)
        optionsHeaderNames.contains(ACCESS_CONTROL_EXPOSE_HEADERS)
        response.status() == HttpStatus.OK
        response.header(ACCESS_CONTROL_ALLOW_ORIGIN) == 'foo.com'

        cleanup:
        embeddedServer.close()
        client.close()

        where:
        singleHeader << [true, false]


    }
}
