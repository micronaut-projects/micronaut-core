package io.micronaut.http.server.netty.cors

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.cors.CrossOrigin
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CrossOriginPreflightRouteMethodSpec extends Specification {
    static final String SPEC_NAME = 'CrossOriginPreflightRouteMethodSpec'
    static final String ORIGIN = 'https://other.example'

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC_NAME])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "a preflight is not approved by the @CrossOrigin of a route for another method"() {
        when:
        HttpResponse<?> getPreflight = send(preflight('GET'))
        HttpResponse<?> postPreflight = send(preflight('POST'))

        then:
        getPreflight.status() == HttpStatus.OK
        getPreflight.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN) == ORIGIN
        postPreflight.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN) == null
    }

    private HttpResponse<?> send(HttpRequest<?> request) {
        try {
            return client.toBlocking().exchange(request)
        } catch (HttpClientResponseException e) {
            return e.response
        }
    }

    private static HttpRequest<?> preflight(String method) {
        HttpRequest.OPTIONS('/cors-route-method')
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, 'content-type')
    }

    @Controller('/cors-route-method')
    @Requires(property = 'spec.name', value = 'CrossOriginPreflightRouteMethodSpec')
    static class RouteMethodController {
        @Get
        @CrossOrigin(allowedOrigins = 'https://other.example', allowCredentials = true)
        String read() {
            'read'
        }

        @Post(consumes = MediaType.APPLICATION_JSON)
        String change(@Body String body) {
            'changed'
        }
    }
}
