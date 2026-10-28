package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Specification

class JdkClientCookieIsolationSpec extends Specification {

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'JdkClientCookieIsolationSpec'])

    // an IP address: the JDK cookie store never matches a cookie of the host 'localhost'
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, new URL("http://127.0.0.1:${server.port}"))

    // one blocking client, as an application injects it: a blocking client made by each toBlocking() has its own cookie store
    BlockingHttpClient blocking = client.toBlocking()

    def 'the cookie of one request is not sent with a later request'() {
        when:
        def first = blocking.retrieve(HttpRequest.GET('/jdk-cookie/cookie').cookie(Cookie.of('SESSION', 'alice').maxAge(3600)))

        then:
        first.contains('SESSION=alice')

        when:
        def second = blocking.retrieve(HttpRequest.GET('/jdk-cookie/cookie'))

        then:
        second == 'none'
    }

    def 'a cookie set by the upstream is not sent with a later request'() {
        when:
        blocking.exchange(HttpRequest.GET('/jdk-cookie/set-cookie'))
        def second = blocking.retrieve(HttpRequest.GET('/jdk-cookie/cookie'))

        then:
        second == 'none'
    }

    @Requires(property = 'spec.name', value = 'JdkClientCookieIsolationSpec')
    @Controller('/jdk-cookie')
    static class CookieController {
        @Get('/set-cookie')
        HttpResponse<?> setCookie() {
            HttpResponse.ok().header(HttpHeaders.SET_COOKIE, 'SESSION=alice; Max-Age=3600; Path=/')
        }

        @Get(value = '/cookie', produces = MediaType.TEXT_PLAIN)
        String cookie(HttpRequest<?> request) {
            request.headers.get(HttpHeaders.COOKIE) ?: 'none'
        }
    }
}
