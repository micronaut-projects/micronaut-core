package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class RawClientCookieSpec extends Specification {

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'RawClientCookieSpec'])

    @AutoCleanup
    RawHttpClient client = server.applicationContext.createBean(RawHttpClient)

    def 'the raw client does not send the cookies an upstream set on a later exchange'() {
        when:
        def first = exchange(HttpRequest.GET(server.URL.toString() + '/raw-cookie/set-cookie'))

        then:
        first.headers.get(HttpHeaders.SET_COOKIE) == 'session=user-a; Path=/'

        when:
        first.close()
        def second = exchange(HttpRequest.GET(server.URL.toString() + '/raw-cookie/cookie'))

        then:
        second.byteBody().buffer().get().toString(StandardCharsets.UTF_8) == 'none'
        ((AbstractJdkHttpClient) client).cookieManager.cookieStore.cookies.empty

        cleanup:
        second?.close()
    }

    def 'the raw client does not keep the cookies of a request'() {
        when:
        def first = exchange(HttpRequest.GET(server.URL.toString() + '/raw-cookie/cookie').cookie(Cookie.of('session', 'user-a')))

        then:
        first.byteBody().buffer().get().toString(StandardCharsets.UTF_8) == 'session=user-a'
        ((AbstractJdkHttpClient) client).cookieManager.cookieStore.cookies.empty

        when:
        first.close()
        def second = exchange(HttpRequest.GET(server.URL.toString() + '/raw-cookie/cookie'))

        then:
        second.byteBody().buffer().get().toString(StandardCharsets.UTF_8) == 'none'

        cleanup:
        second?.close()
    }

    private ByteBodyHttpResponse<?> exchange(HttpRequest<?> request) {
        Mono.from(client.exchange(request, null, null)).cast(ByteBodyHttpResponse).block()
    }

    @Requires(property = 'spec.name', value = 'RawClientCookieSpec')
    @Controller('/raw-cookie')
    static class CookieController {
        @Get('/set-cookie')
        HttpResponse<?> setCookie() {
            HttpResponse.ok().header(HttpHeaders.SET_COOKIE, 'session=user-a; Path=/')
        }

        @Get(value = '/cookie', produces = MediaType.TEXT_PLAIN)
        String cookie(HttpRequest<?> request) {
            request.headers.get(HttpHeaders.COOKIE) ?: 'none'
        }
    }
}
