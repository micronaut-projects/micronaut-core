package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Specification

class JdkClientCookieIsolationSpec extends Specification {

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'JdkClientCookieIsolationSpec'])

    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    def 'the cookie of one request is not sent with a later request'() {
        when:
        def first = client.toBlocking().retrieve(HttpRequest.GET('/jdk-cookie/cookie').cookie(Cookie.of('SESSION', 'alice')))

        then:
        first.contains('SESSION=alice')

        when:
        def second = client.toBlocking().retrieve(HttpRequest.GET('/jdk-cookie/cookie'))

        then:
        second == 'none'
    }

    def 'a cookie set by the upstream is not sent with a later request'() {
        when:
        client.toBlocking().exchange(HttpRequest.GET('/jdk-cookie/set-cookie'))
        def second = client.toBlocking().retrieve(HttpRequest.GET('/jdk-cookie/cookie'))

        then:
        second == 'none'
    }

    @Requires(property = 'spec.name', value = 'JdkClientCookieIsolationSpec')
    @Controller('/jdk-cookie')
    static class CookieController {
        @Get('/set-cookie')
        HttpResponse<?> setCookie() {
            HttpResponse.ok().header(HttpHeaders.SET_COOKIE, 'SESSION=alice; Path=/')
        }

        @Get(value = '/cookie', produces = MediaType.TEXT_PLAIN)
        String cookie(HttpRequest<?> request) {
            request.headers.get(HttpHeaders.COOKIE) ?: 'none'
        }
    }
}
