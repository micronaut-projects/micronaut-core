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

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import io.micronaut.http.*
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.cors.CorsUtil
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.http.HttpHeaders.*

@Property(name = "spec.name", value = SPEC_NAME)
@Property(name = "micronaut.server.cors.enabled", value = StringUtils.TRUE)
@MicronautTest
class CorsFilterSpec extends Specification {
    private final static String SPEC_NAME = "CorsFilterSpec"

    @Inject
    @Client("/")
    HttpClient httpClient

    @Unroll
    void "it is possible to use a regular expression to match cors origins"(String regex, String origin) {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                "spec.name": SPEC_NAME,
                "micronaut.server.cors.enabled": StringUtils.TRUE,
                "micronaut.server.cors.configurations.foo.allowed-origins-regex": regex
        ])
        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())
        BlockingHttpClient client = httpClient.toBlocking()
        HttpRequest request = HttpRequest.OPTIONS("/example")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'GET')

        and:
        CorsUtil.isPreflightRequest(request)

        when:
        HttpResponse<?> response = client.exchange(request)

        then:
        HttpStatus.OK == response.status()
        response.headers.names().size() == 6

        response.headers.contains(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)
        response.headers.contains(HttpHeaders.ACCESS_CONTROL_MAX_AGE)
        response.headers.contains(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
        response.headers.contains(HttpHeaders.VARY)
        response.headers.contains(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)
        response.headers.contains(HttpHeaders.CONTENT_LENGTH)
        [origin] == response.headers.getAll(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
        ['Origin'] == response.headers.getAll(VARY)
        [StringUtils.TRUE] == response.headers.getAll(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)

        cleanup:
        httpClient.close()
        embeddedServer.close()

        where:
        regex                                                | origin
        '.*'                                                     | 'http://www.bar.com'
        '^http://www\\.(foo|bar)\\.com$'    | 'http://www.bar.com'
        '^http://www\\.(foo|bar)\\.com$'    | 'http://www.foo.com'
        '.*(bar|foo)$'                                    | 'asdfasdf foo'
        '.*(bar|foo)$'                                    | 'asdfasdf bar'
        '.*(bar|foo)$'                                    | 'http://asdfasdf.foo'
        '.*(bar|foo)$'                                    | 'http://asdfasdf.bar'
    }

    @Property(name = "micronaut.server.cors.configurations.foo.allowed-origins", value = "http://www.foo.com")
    @Property(name = "micronaut.server.cors.configurations.foo.allowed-methods", value = "GET")
    void "test handleRequest with disallowed method"() {
        given:
        HttpRequest<?> request = HttpRequest.OPTIONS("/example").header(HttpHeaders.ORIGIN, 'http://www.foo.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'GET')
        and:
        CorsUtil.isPreflightRequest(request)

        when:
        HttpResponse<?> response = execute(request)

        then:
        noExceptionThrown()
        response.status() == HttpStatus.OK
        containsAccessControlHeaders(response)

        when:
        request = HttpRequest.OPTIONS("/example").header(HttpHeaders.ORIGIN, 'http://www.foo.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'PUT')
        response = execute(request)

        then:
        noExceptionThrown()
        response.status() == HttpStatus.FORBIDDEN
        !containsAccessControlHeaders(response)
    }

    @Property(name = "micronaut.server.cors.configurations.foo.allowed-origins", value = "http://www.foo.com")
    @Property(name = "micronaut.server.cors.configurations.foo.allowed-methods", value = "GET")
    void "GET request with origin is passed through because origin matches"() {
        given:
        HttpRequest request = HttpRequest.create(HttpMethod.GET, "/example").header(HttpHeaders.ORIGIN, 'http://www.foo.com')

        and:
        !CorsUtil.isPreflightRequest(request)

        when:
        HttpResponse<?> response = execute(request)

        then:
        HttpStatus.OK == response.status()
        response.headers.names().size() == 6
        response.headers.contains(HttpHeaders.DATE)
        response.headers.contains(HttpHeaders.CONTENT_TYPE)
        response.headers.contains(HttpHeaders.CONTENT_LENGTH)
        response.headers.contains(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
        response.headers.contains(HttpHeaders.VARY)
        response.headers.contains(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)
        ['http://www.foo.com'] == response.headers.getAll(ACCESS_CONTROL_ALLOW_ORIGIN)
        ['Origin'] == response.headers.getAll(HttpHeaders.VARY)
        [StringUtils.TRUE] == response.headers.getAll(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)
    }

    @Property(name = "micronaut.server.cors.configurations.foo.allowed-origins", value = "http://www.foo.com")
    @Property(name = "micronaut.server.cors.configurations.foo.allowed-methods", value = "GET")
    @Property(name = "micronaut.server.cors.configurations.foo.allowed-headers", value = "foo")
    void "preflight rejected because the access control request headers does not match the allowed headers in configuration"() {
        given:
        HttpRequest request = HttpRequest.OPTIONS("/example")
                .header(HttpHeaders.ORIGIN, 'http://www.foo.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'GET')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, 'bar')

        when:
        HttpResponse<?> response = execute(request)

        then: "the request is rejected because bar is not allowed"
        HttpStatus.FORBIDDEN == response.status()

        when:
        request = HttpRequest.OPTIONS("/example")
                .header(HttpHeaders.ORIGIN, 'http://www.foo.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'GET')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, 'foo')
        response = execute(request)

        then: "the preflight request is accepted because foo header is allowed"
        HttpStatus.OK == response.status()
    }

    @Property(name = "micronaut.server.cors.configurations.foo.allowed-origins", value = "http://www.foo.com")
    @Property(name = "micronaut.server.cors.configurations.foo.allowed-methods", value = "GET")
    @Property(name = "micronaut.server.cors.configurations.foo.allowed-headers", value = "foo,bar")
    void "A preflight request with Access-Control-Request-Headers matching allowed headers in configuration is let through"() {
        given:
        HttpRequest request = HttpRequest.OPTIONS("/example")
                .header(HttpHeaders.ORIGIN, 'http://www.foo.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'GET')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, 'foo')

        when:
        HttpResponse<?> response = execute(request)

        then:
        HttpStatus.OK == response.status()
        response.headers.names().size() == 7
        response.headers.contains(HttpHeaders.CONTENT_LENGTH)

        response.headers.contains(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
        response.headers.contains(HttpHeaders.VARY)
        response.headers.contains(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)
        response.headers.contains(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)
        response.headers.contains(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)
        response.headers.contains(HttpHeaders.ACCESS_CONTROL_MAX_AGE)

        ['http://www.foo.com'] == response.headers.getAll(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
        ['Origin'] == response.headers.getAll(HttpHeaders.VARY)
        [StringUtils.TRUE] == response.headers.getAll(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)
        ['GET']  == response.headers.getAll(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)
        ['foo']  == response.headers.getAll(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)
        ['1800']  == response.headers.getAll(HttpHeaders.ACCESS_CONTROL_MAX_AGE)
    }


    @Property(name = "micronaut.server.cors.configurations.foo.allowed-origins", value = "http://www.foo.com")
    @Property(name = "micronaut.server.cors.configurations.foo.allowed-methods", value = "GET")
    void "a GET request with origin header  is let through even if origins don't match"() {
        given:
        HttpRequest request = HttpRequest.create(HttpMethod.GET, "/example").header(HttpHeaders.ORIGIN, 'http://www.bar.com')

        when:
        HttpResponse<?> response = execute(request)

        then:
        HttpStatus.FORBIDDEN == response.status()

        and:
        !containsAccessControlHeaders(response)
    }

    @Property(name = "micronaut.server.cors.configurations.foo.allowed-origins", value = "http://www.foo.com")
    @Property(name = "micronaut.server.cors.configurations.foo.exposed-headers", value = "Foo-Header,Bar-Header")
    void "server includes exposed headers in a response to a preflight request if they are set in configuration"() {
        given:
        HttpRequest request = HttpRequest.OPTIONS("/example")
                .header(HttpHeaders.ORIGIN, 'http://www.foo.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'GET')

        when:
        HttpResponse<?> response = execute(request)

        then:
        response.status() == HttpStatus.OK
        7 == response.headers.names().size()
        response.headers.contains(HttpHeaders.CONTENT_LENGTH)
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_ORIGIN) == 'http://www.foo.com' // The origin is echo'd
        response.getHeaders().get(VARY) == 'Origin' // The vary header is set
        response.getHeaders().getAll(ACCESS_CONTROL_EXPOSE_HEADERS) == ['Foo-Header', 'Bar-Header' ]// Expose headers are set from config
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_CREDENTIALS) == 'true' // Allow credentials header is set
        response.getHeaders().get(ACCESS_CONTROL_MAX_AGE) == '1800'
    }

    @Property(name = "micronaut.server.cors.configurations.foo.allowed-origins", value = "http://www.foo.com")
    @Property(name = "micronaut.server.cors.configurations.foo.exposed-headers", value = "Foo-Header,Bar-Header")
    void "preflight request with Access-Control-Request-Headers but no allowed headers in configuration goes through. Exposed headers in configuration are echoed in response. Access-Control-Request-Headers  are echoed in response"() {
        given:
        HttpRequest request = HttpRequest.OPTIONS("/example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, 'X-Header')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, 'Y-Header')
                .header(HttpHeaders.ORIGIN, 'http://www.foo.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'GET')

        when:
        HttpResponse<?> response = execute(request)

        then:
        HttpStatus.OK == response.status()
        response.headers.names().size() == 8
        response.getHeaders().contains(HttpHeaders.CONTENT_LENGTH)
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_METHODS) == 'GET'
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_ORIGIN) == 'http://www.foo.com' // The origin is echo'd
        response.getHeaders().get(VARY) == 'Origin' // The vary header is set
        response.getHeaders().getAll(ACCESS_CONTROL_EXPOSE_HEADERS) == ['Foo-Header', 'Bar-Header'] // Expose headers are set from config
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_CREDENTIALS) == 'true' // Allow credentials header is set
        response.getHeaders().getAll(ACCESS_CONTROL_ALLOW_HEADERS) == ['X-Header', 'Y-Header'] // Allow headers are echo'd from the request
        response.getHeaders().get(ACCESS_CONTROL_MAX_AGE) == '1800' // Max age is set from config
    }

    @Property(name = "micronaut.server.cors.single-header", value = StringUtils.TRUE)
    @Property(name = "micronaut.server.cors.configurations.foo.exposed-headers", value = "Foo-Header,Bar-Header")
    void "if single-headers is set in configuration the response headers' values are comma separated"() {
        given:
        HttpRequest request = HttpRequest.OPTIONS("/example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, 'X-Header')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, 'Y-Header')
                .header(HttpHeaders.ORIGIN, 'http://www.foo.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'GET')

        when:
        HttpResponse<?> response = execute(request)

        then:
        HttpStatus.OK == response.status()

        then: "the response is not modified"
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_METHODS) == 'GET'
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_ORIGIN) == 'http://www.foo.com' // The origin is echo'd
        response.getHeaders().get(VARY) == 'Origin' // The vary header is set
        response.getHeaders().get(ACCESS_CONTROL_EXPOSE_HEADERS) == 'Foo-Header,Bar-Header' // Expose headers are set from config
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_CREDENTIALS) == 'true' // Allow credentials header is set
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_HEADERS) == 'X-Header,Y-Header' // Allow headers are echo'd from the request
        response.getHeaders().get(ACCESS_CONTROL_MAX_AGE) == '1800' // Max age is set from config
    }

    @Property(name = "micronaut.server.cors.configurations.foo.allowed-origins", value = "http://www.foo.com")
    @Property(name = "micronaut.server.cors.configurations.foo.allowed-methods", value = "GET")
    void "A preflight request is rejected for a non-existing route"() {
        given:
        HttpRequest request = HttpRequest.OPTIONS("/doesnt-exists-route")
                .header(HttpHeaders.ORIGIN, 'http://www.foo.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'GET')

        when:
        HttpResponse<?> response = execute(request)

        then:
        HttpStatus.FORBIDDEN == response.status()
    }

    @Property(name = "micronaut.server.cors.configurations.foo.allowed-origins", value = "http://www.foo.com")
    @Property(name = "micronaut.server.cors.configurations.foo.exposed-headers", value = "Foo-Header,Bar-Header")
    void "A preflight request is rejected for a route that does exist but doesn't handle the requested HTTP Method"() {
        given:
        HttpRequest request = HttpRequest.OPTIONS("/example")
                .header(HttpHeaders.ORIGIN, 'http://www.foo.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'POST')

        when:
        HttpResponse<?> response = execute(request)

        then:
        HttpStatus.FORBIDDEN == response.status()
    }

    @Requires(property = "spec.name", value = "CorsFilterSpec")
    @Controller
    static class TestController{

        @Get("/example")
        String example() { return "Example"}
    }

    private HttpResponse<?> execute(HttpRequest<?> req) {
        try {
            return httpClient.toBlocking().exchange(req)
        } catch (HttpClientResponseException e) {
            return e.response
        }
    }

    private static boolean containsAccessControlHeaders(HttpResponse<?> response) {
        response.headers.names().stream().anyMatch { it.startsWith("Access-Control-Allow-") }
    }
}
