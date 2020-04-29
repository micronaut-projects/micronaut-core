package io.micronaut.http.server.netty.cors

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
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
        RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient, embeddedServer.getURL())

        def optionsResponse = client.exchange(
                HttpRequest.OPTIONS('/test/arbitrary')
                        .header(ORIGIN, 'foo.com')
                        .header(ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET)
                        .header(ACCESS_CONTROL_REQUEST_HEADERS, "${HttpHeaders.CONTENT_TYPE},${HttpHeaders.ACCEPT}")
        ).blockingFirst()
        Set<String> optionsHeaderNames = optionsResponse.headers.names()

        def response = client.exchange(
                HttpRequest.GET('/test/arbitrary')
                        .header(ORIGIN, 'foo.com')
        ).blockingFirst()



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
