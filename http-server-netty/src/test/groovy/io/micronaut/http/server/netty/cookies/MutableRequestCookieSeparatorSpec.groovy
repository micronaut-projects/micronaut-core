package io.micronaut.http.server.netty.cookies

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.HttpClient
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * A cookie added to the mutable view of a server request must not inject another cookie pair
 * into the Cookie header, for example when the request is relayed by a proxy client.
 */
class MutableRequestCookieSeparatorSpec extends Specification {
    static final String SPEC_NAME = 'MutableRequestCookieSeparatorSpec'

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC_NAME])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "a cookie value with a separator is rejected"() {
        expect:
        client.toBlocking().retrieve(HttpRequest.GET('/mutable-cookie?value=' + URLEncoder.encode(value, 'UTF-8'))) == expected

        where:
        value             | expected
        'EUR'             | 'tenant=EUR'
        'EUR; role=staff' | 'rejected'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Controller('/mutable-cookie')
    static class CookieController {
        @Get(produces = 'text/plain')
        String add(HttpRequest<?> request, @QueryValue String value) {
            try {
                return request.mutate().cookie(Cookie.of('tenant', value)).headers.get('Cookie')
            } catch (IllegalArgumentException ignored) {
                return 'rejected'
            }
        }
    }
}
