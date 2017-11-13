package org.particleframework.http.server.netty.cors

import okhttp3.FormBody
import okhttp3.Request
import org.particleframework.context.annotation.Requires
import org.particleframework.http.HttpResponse
import org.particleframework.http.HttpStatus
import org.particleframework.http.server.netty.AbstractParticleSpec
import org.particleframework.http.annotation.Controller
import org.particleframework.web.router.annotation.Get

import static org.particleframework.http.HttpHeaders.*

class NettyCorsSpec extends AbstractParticleSpec {

    void "test non cors request"() {
        given:
        def request = new Request.Builder()
                .url("$server/test")

        when:
        def response = client.newCall(
                request.build()
        ).execute()
        Set<String> headerNames = response.headers().names()

        then:
        response.code() == HttpStatus.NO_CONTENT.code
        headerNames.empty
    }

    void "test cors request without configuration"() {
        given:
        def request = new Request.Builder()
                .url("$server/test")
                .header(ORIGIN, 'fooBar.com')

        when:
        def response = client.newCall(
                request.build()
        ).execute()
        Set<String> headerNames = response.headers().names()

        then:
        response.code() == HttpStatus.NO_CONTENT.code
        headerNames.empty
    }

    void "test cors request with a controller that returns map"() {
        given:
        def request = new Request.Builder()
                .url("$server/test/arbitrary")
                .header(ORIGIN, 'foo.com')

        when:
        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.header(ACCESS_CONTROL_ALLOW_ORIGIN) == 'foo.com'
        response.header(VARY) == ORIGIN
        !response.headers().names().contains(ACCESS_CONTROL_MAX_AGE)
        !response.headers().names().contains(ACCESS_CONTROL_ALLOW_HEADERS)
        !response.headers().names().contains(ACCESS_CONTROL_ALLOW_METHODS)
        !response.headers().names().contains(ACCESS_CONTROL_EXPOSE_HEADERS)
        response.header(ACCESS_CONTROL_ALLOW_CREDENTIALS) == 'true'
    }

    void "test cors request with controlled method"() {
        given:
        def request = new Request.Builder()
                .url("$server/test")
                .header(ORIGIN, 'foo.com')

        when:
        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.NO_CONTENT.code
        response.header(ACCESS_CONTROL_ALLOW_ORIGIN) == 'foo.com'
        response.header(VARY) == ORIGIN
        !response.headers().names().contains(ACCESS_CONTROL_MAX_AGE)
        !response.headers().names().contains(ACCESS_CONTROL_ALLOW_HEADERS)
        !response.headers().names().contains(ACCESS_CONTROL_ALLOW_METHODS)
        !response.headers().names().contains(ACCESS_CONTROL_EXPOSE_HEADERS)
        response.header(ACCESS_CONTROL_ALLOW_CREDENTIALS) == 'true'
    }

    void "test cors request with controlled headers"() {
        given:
        def request = new Request.Builder()
                .url("$server/test")
                .header(ORIGIN, 'bar.com')
                .header(ACCEPT, 'application/json')

        when:
        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.NO_CONTENT.code
        response.header(ACCESS_CONTROL_ALLOW_ORIGIN) == 'bar.com'
        response.header(VARY) == ORIGIN
        !response.headers().names().contains(ACCESS_CONTROL_MAX_AGE)
        !response.headers().names().contains(ACCESS_CONTROL_ALLOW_HEADERS)
        !response.headers().names().contains(ACCESS_CONTROL_ALLOW_METHODS)
        response.headers(ACCESS_CONTROL_EXPOSE_HEADERS) == ['x', 'y']
        !response.headers().names().contains(ACCESS_CONTROL_ALLOW_CREDENTIALS)
    }

    void "test cors request with invalid method"() {
        given:
        def request = new Request.Builder()
                .url("$server/test")
                .method('POST', new FormBody.Builder().build())
                .header(ORIGIN, 'foo.com')

        when:
        def response = client.newCall(
                request.build()
        ).execute()
        Set<String> headerNames = response.headers().names()

        then:
        response.code() == HttpStatus.FORBIDDEN.code
        headerNames.empty
    }

    void "test cors request with invalid header"() {
        given:
        def request = new Request.Builder()
                .url("$server/test")
                .header(ORIGIN, 'bar.com')
                .header(ACCESS_CONTROL_REQUEST_HEADERS, 'Foo, Accept')

        when:
        def response = client.newCall(
                request.build()
        ).execute()

        then: "it passes through because only preflight requests check allowed headers"
        response.code() == HttpStatus.NO_CONTENT.code
    }

    void "test preflight request with invalid header"() {
        given:
        def request = new Request.Builder()
                .url("$server/test")
                .method('OPTIONS', new FormBody.Builder().build())
                .header(ACCESS_CONTROL_REQUEST_METHOD, 'GET')
                .header(ORIGIN, 'bar.com')
                .header(ACCESS_CONTROL_REQUEST_HEADERS, 'Foo, Accept')

        when:
        def response = client.newCall(
                request.build()
        ).execute()

        then: "it fails because preflight requests check allowed headers"
        response.code() == HttpStatus.FORBIDDEN.code
    }

    void "test preflight request with invalid method"() {
        given:
        def request = new Request.Builder()
                .url("$server/test")
                .method('OPTIONS', new FormBody.Builder().build())
                .header(ACCESS_CONTROL_REQUEST_METHOD, 'POST')
                .header(ORIGIN, 'foo.com')

        when:
        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.FORBIDDEN.code
    }

    void "test preflight request with controlled method"() {
        given:
        def request = new Request.Builder()
                .url("$server/test")
                .method('OPTIONS', new FormBody.Builder().build())
                .header(ACCESS_CONTROL_REQUEST_METHOD, 'GET')
                .header(ORIGIN, 'foo.com')
                .header(ACCESS_CONTROL_REQUEST_HEADERS, 'Foo, Bar')


        when:
        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.header(ACCESS_CONTROL_ALLOW_METHODS) == 'GET'
        response.headers(ACCESS_CONTROL_ALLOW_HEADERS) == ['Foo', 'Bar']
        !response.headers().names().contains(ACCESS_CONTROL_MAX_AGE)
        response.header(ACCESS_CONTROL_ALLOW_ORIGIN) == 'foo.com'
        response.header(VARY) == ORIGIN
        !response.headers().names().contains(ACCESS_CONTROL_EXPOSE_HEADERS)
        response.header(ACCESS_CONTROL_ALLOW_CREDENTIALS) == 'true'
    }

    void "test preflight request with controlled headers"() {
        given:
        def request = new Request.Builder()
                .url("$server/test")
                .method('OPTIONS', new FormBody.Builder().build())
                .header(ACCESS_CONTROL_REQUEST_METHOD, 'POST')
                .header(ORIGIN, 'bar.com')
                .header(ACCESS_CONTROL_REQUEST_HEADERS, 'Accept')


        when:
        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.header(ACCESS_CONTROL_ALLOW_METHODS) == 'POST'
        response.headers(ACCESS_CONTROL_ALLOW_HEADERS) == ['Accept']
        response.header(ACCESS_CONTROL_MAX_AGE) == '150'
        response.header(ACCESS_CONTROL_ALLOW_ORIGIN) == 'bar.com'
        response.header(VARY) == ORIGIN
        response.headers(ACCESS_CONTROL_EXPOSE_HEADERS) == ['x', 'y']
        !response.headers().names().contains(ACCESS_CONTROL_ALLOW_CREDENTIALS)
    }

    Map<String, Object> getConfiguration() {
        ['particle.server.cors.enabled': true,
        'particle.server.cors.configurations.foo.allowedOrigins': ['foo.com'],
        'particle.server.cors.configurations.foo.allowedMethods': ['GET'],
        'particle.server.cors.configurations.foo.maxAge': -1,
        'particle.server.cors.configurations.bar.allowedOrigins': ['bar.com'],
        'particle.server.cors.configurations.bar.allowedHeaders': ['Content-Type', 'Accept'],
        'particle.server.cors.configurations.bar.exposedHeaders': ['x', 'y'],
        'particle.server.cors.configurations.bar.maxAge': 150,
        'particle.server.cors.configurations.bar.allowCredentials': false]
    }

    @Controller
    @Requires(property = 'spec.name', value = 'NettyCorsSpec')
    static class TestController {

        HttpResponse index() {
            HttpResponse.noContent()
        }

        @Get
        Map arbitrary() {
            [some: 'data']
        }
    }
}
