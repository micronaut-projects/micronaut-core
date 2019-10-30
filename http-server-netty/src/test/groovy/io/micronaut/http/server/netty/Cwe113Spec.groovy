package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.cookie.Cookie
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

@Issue("https://cwe.mitre.org/data/definitions/113.html")
class Cwe113Spec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared @AutoCleanup RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test http response splitting on header"() {
        when:
        URI uri = UriBuilder.of("/user-input/header")
        .queryParam("param", "value\r\nX-Injected: injected")
        .build()
        def resp = client.exchange(HttpRequest.GET(uri), String.class).blockingFirst()

        then:
        thrown(HttpClientResponseException)

        when:
        uri = UriBuilder.of("/user-input/header")
                .queryParam("param", "value\r\n X-Injected: injected\r\n")
                .build()
        resp = client.exchange(HttpRequest.GET(uri), String.class).blockingFirst()

        then:
        thrown(HttpClientResponseException)

        when:
        uri = UriBuilder.of("/user-input/header")
                .queryParam("param", "value\r\n X-Injected: injected")
                .build()
        resp = client.exchange(HttpRequest.GET(uri), String.class).blockingFirst()

        then:
        noExceptionThrown()
        resp.header("X-Header") == "value X-Injected: injected"
    }

    void "test http response splitting on cookie"() {
        when:
        URI uri = UriBuilder.of("/user-input/cookie")
                .queryParam("param", "value\r\nX-Injected: injected")
                .build()
        def resp = client.exchange(HttpRequest.GET(uri), String.class).blockingFirst()

        then:
        thrown(HttpClientResponseException)

        when:
        uri = UriBuilder.of("/user-input/cookie")
                .queryParam("param", "value\r\n X-Injected: injected\r\n")
                .build()
        resp = client.exchange(HttpRequest.GET(uri), String.class).blockingFirst()

        then:
        thrown(HttpClientResponseException)

        when:
        uri = UriBuilder.of("/user-input/cookie")
                .queryParam("param", "value\r\n X-Injected: injected")
                .build()
        resp = client.exchange(HttpRequest.GET(uri), String.class).blockingFirst()

        then:
        noExceptionThrown()
        resp.header("set-cookie") == "X-Cookie=value X-Injected: injected"
    }

    @Controller("/user-input")
    static class UserInputController {

        @Get("/header{?param}")
        HttpResponse<String> header(String param) {
            HttpResponse.ok("header").header("X-Header", param)
        }

        @Get("/cookie{?param}")
        HttpResponse<String> cookie(String param) {
            HttpResponse.ok("cookie").cookie(Cookie.of("X-Cookie", param))
        }
    }
}
