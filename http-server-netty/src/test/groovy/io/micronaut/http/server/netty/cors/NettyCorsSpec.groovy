/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.cors

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Error

import static io.micronaut.http.HttpHeaders.*

class NettyCorsSpec extends AbstractMicronautSpec {

    void "test non cors request"() {
        when:
        def response = rxClient.exchange('/test').blockingFirst()
        Set<String> headerNames = response.getHeaders().names()

        then:
        response.status == HttpStatus.NO_CONTENT
        response.contentLength == -1
        headerNames.size() == 1
        headerNames.contains("connection")
    }

    void "test cors request without configuration"() {
        given:
        def response = rxClient.exchange(
                HttpRequest.GET('/test')
                           .header(ORIGIN, 'fooBar.com')
        ).blockingFirst()

        when:
        Set<String> headerNames = response.headers.names()

        then:
        response.status == HttpStatus.NO_CONTENT
        headerNames.size() == 1
        headerNames.contains("connection")
    }

    void "test cors request with a controller that returns map"() {
        given:
        def response = rxClient.exchange(
                HttpRequest.GET('/test/arbitrary')
                        .header(ORIGIN, 'foo.com')
        ).blockingFirst()

        when:
        Set<String> headerNames = response.headers.names()

        then:
        response.status == HttpStatus.OK
        response.header(ACCESS_CONTROL_ALLOW_ORIGIN) == 'foo.com'
        response.header(VARY) == ORIGIN
        !headerNames.contains(ACCESS_CONTROL_MAX_AGE)
        !headerNames.contains(ACCESS_CONTROL_ALLOW_HEADERS)
        !headerNames.contains(ACCESS_CONTROL_ALLOW_METHODS)
        !headerNames.contains(ACCESS_CONTROL_EXPOSE_HEADERS)
        response.header(ACCESS_CONTROL_ALLOW_CREDENTIALS) == 'true'
    }

    void "test cors request with controlled method"() {
        given:
        def response = rxClient.exchange(
                HttpRequest.GET('/test')
                        .header(ORIGIN, 'foo.com')
        ).blockingFirst()

        when:
        Set<String> headerNames = response.headers.names()

        then:
        response.status == HttpStatus.NO_CONTENT
        response.header(ACCESS_CONTROL_ALLOW_ORIGIN) == 'foo.com'
        response.header(VARY) == ORIGIN
        !headerNames.contains(ACCESS_CONTROL_MAX_AGE)
        !headerNames.contains(ACCESS_CONTROL_ALLOW_HEADERS)
        !headerNames.contains(ACCESS_CONTROL_ALLOW_METHODS)
        !headerNames.contains(ACCESS_CONTROL_EXPOSE_HEADERS)
        response.header(ACCESS_CONTROL_ALLOW_CREDENTIALS) == 'true'
    }

    void "test cors request with controlled headers"() {
        given:
        def response = rxClient.exchange(
                HttpRequest.GET('/test')
                        .header(ORIGIN, 'bar.com')
                        .header(ACCEPT, 'application/json')

        ).blockingFirst()

        when:
        Set<String> headerNames = response.headers.names()

        then:
        response.code() == HttpStatus.NO_CONTENT.code
        response.header(ACCESS_CONTROL_ALLOW_ORIGIN) == 'bar.com'
        response.header(VARY) == ORIGIN
        !headerNames.contains(ACCESS_CONTROL_MAX_AGE)
        !headerNames.contains(ACCESS_CONTROL_ALLOW_HEADERS)
        !headerNames.contains(ACCESS_CONTROL_ALLOW_METHODS)
        response.headers.getAll(ACCESS_CONTROL_EXPOSE_HEADERS) == ['x', 'y']
        !headerNames.contains(ACCESS_CONTROL_ALLOW_CREDENTIALS)
    }

    void "test cors request with invalid method"() {
        given:
        def response = rxClient.exchange(
                HttpRequest.POST('/test', [:])
                        .header(ORIGIN, 'foo.com')

        ).onErrorReturn({ t -> t.response} ).blockingFirst()

        when:
        Set<String> headerNames = response.headers.names()

        then:
        response.code() == HttpStatus.FORBIDDEN.code
        headerNames == ['connection'] as Set
    }

    void "test cors request with invalid header"() {
        given:
        def response = rxClient.exchange(
                HttpRequest.GET('/test')
                        .header(ORIGIN, 'bar.com')
                        .header(ACCESS_CONTROL_REQUEST_HEADERS, 'Foo, Accept')

        ).blockingFirst()

        expect: "it passes through because only preflight requests check allowed headers"
        response.code() == HttpStatus.NO_CONTENT.code
    }

    void "test preflight request with invalid header"() {
        given:
        def response = rxClient.exchange(
                HttpRequest.OPTIONS('/test')
                        .header(ACCESS_CONTROL_REQUEST_METHOD, 'GET')
                        .header(ORIGIN, 'bar.com')
                        .header(ACCESS_CONTROL_REQUEST_HEADERS, 'Foo, Accept')

        ).onErrorReturn({ t -> t.response} ).blockingFirst()

        expect: "it fails because preflight requests check allowed headers"
        response.code() == HttpStatus.FORBIDDEN.code
    }

    void "test preflight request with invalid method"() {
        given:
        def response = rxClient.exchange(
                HttpRequest.OPTIONS('/test')
                        .header(ACCESS_CONTROL_REQUEST_METHOD, 'POST')
                        .header(ORIGIN, 'foo.com')

        ).onErrorReturn({ t -> t.response} ).blockingFirst()

        expect:
        response.code() == HttpStatus.FORBIDDEN.code
    }

    void "test preflight request with controlled method"() {
        given:
        def response = rxClient.exchange(
                HttpRequest.OPTIONS('/test')
                        .header(ACCESS_CONTROL_REQUEST_METHOD, 'GET')
                        .header(ORIGIN, 'foo.com')
                        .header(ACCESS_CONTROL_REQUEST_HEADERS, 'Foo, Bar')

        ).blockingFirst()

        def headerNames = response.headers.names()

        expect:
        response.code() == HttpStatus.OK.code
        response.header(ACCESS_CONTROL_ALLOW_METHODS) == 'GET'
        response.headers.getAll(ACCESS_CONTROL_ALLOW_HEADERS) == ['Foo', 'Bar']
        !headerNames.contains(ACCESS_CONTROL_MAX_AGE)
        response.header(ACCESS_CONTROL_ALLOW_ORIGIN) == 'foo.com'
        response.header(VARY) == ORIGIN
        !headerNames.contains(ACCESS_CONTROL_EXPOSE_HEADERS)
        response.header(ACCESS_CONTROL_ALLOW_CREDENTIALS) == 'true'
    }

    void "test preflight request with controlled headers"() {

        given:
        def response = rxClient.exchange(
                HttpRequest.OPTIONS('/test')
                        .header(ACCESS_CONTROL_REQUEST_METHOD, 'POST')
                        .header(ORIGIN, 'bar.com')
                        .header(ACCESS_CONTROL_REQUEST_HEADERS, 'Accept')
        ).blockingFirst()

        def headerNames = response.headers.names()

        expect:
        response.code() == HttpStatus.OK.code
        response.header(ACCESS_CONTROL_ALLOW_METHODS) == 'POST'
        response.headers.getAll(ACCESS_CONTROL_ALLOW_HEADERS) == ['Accept']
        response.header(ACCESS_CONTROL_MAX_AGE) == '150'
        response.header(ACCESS_CONTROL_ALLOW_ORIGIN) == 'bar.com'
        response.header(VARY) == ORIGIN
        response.headers.getAll(ACCESS_CONTROL_EXPOSE_HEADERS) == ['x', 'y']
        !headerNames.contains(ACCESS_CONTROL_ALLOW_CREDENTIALS)
    }

    void "test control headers are applied to error response routes"() {
        when:
        rxClient.exchange(
                HttpRequest.GET('/test/error')
                        .header(ORIGIN, 'foo.com')
        ).blockingFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.status == HttpStatus.BAD_REQUEST
        ex.response.header(ACCESS_CONTROL_ALLOW_ORIGIN) == 'foo.com'
        ex.response.header(VARY) == ORIGIN
    }

    void "test control headers are applied to error responses with no handler"() {
        when:
        rxClient.exchange(
                HttpRequest.GET('/test/error-checked')
                        .header(ORIGIN, 'foo.com')
        ).blockingFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.status == HttpStatus.INTERNAL_SERVER_ERROR
        ex.response.header(ACCESS_CONTROL_ALLOW_ORIGIN) == 'foo.com'
        ex.response.header(VARY) == ORIGIN
    }

    void "test control headers are applied to http error responses"() {
        when:
        rxClient.exchange(
                HttpRequest.GET('/test/error-response')
                        .header(ORIGIN, 'foo.com')
        ).blockingFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.status == HttpStatus.BAD_REQUEST
        ex.response.header(ACCESS_CONTROL_ALLOW_ORIGIN) == 'foo.com'
        ex.response.headers.getAll(ACCESS_CONTROL_ALLOW_ORIGIN).size() == 1
        ex.response.header(VARY) == ORIGIN
    }

    @Override
    Map<String, Object> getConfiguration() {
        ['micronaut.server.cors.enabled': true,
        'micronaut.server.cors.configurations.foo.allowedOrigins': ['foo.com'],
        'micronaut.server.cors.configurations.foo.allowedMethods': ['GET'],
        'micronaut.server.cors.configurations.foo.maxAge': -1,
        'micronaut.server.cors.configurations.bar.allowedOrigins': ['bar.com'],
        'micronaut.server.cors.configurations.bar.allowedHeaders': ['Content-Type', 'Accept'],
        'micronaut.server.cors.configurations.bar.exposedHeaders': ['x', 'y'],
        'micronaut.server.cors.configurations.bar.maxAge': 150,
        'micronaut.server.cors.configurations.bar.allowCredentials': false,
        'micronaut.server.dateHeader': false]
    }

    @Controller('/test')
    @Requires(property = 'spec.name', value = 'NettyCorsSpec')
    static class TestController {

        @Get
        HttpResponse index() {
            HttpResponse.noContent()
        }

        @Get('/arbitrary')
        Map arbitrary() {
            [some: 'data']
        }

        @Get("/error")
        String error() {
            throw new RuntimeException("error")
        }

        @Get("/error-checked")
        String errorChecked() {
            throw new IOException("error")
        }

        @Get("/error-response")
        HttpResponse errorResponse() {
            HttpResponse.badRequest()
        }

        @Error(exception = RuntimeException)
        HttpResponse onError() {
            HttpResponse.badRequest()
        }
    }
}
