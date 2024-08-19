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
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Property
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.util.StringUtils
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.util.HttpHostResolver
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_MAX_AGE
import static io.micronaut.http.HttpHeaders.VARY

@Property(name = "micronaut.server.cors.enabled", value = "true")
@Property(name = "micronaut.server.dispatch-options-requests", value = "true")
@MicronautTest
class CorsFilterSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient httpClient

    void "non CORS request is passed through"() {
        given:
        def request = HttpRequest.create(HttpMethod.OPTIONS, "/example")

        when:
        HttpResponse<?> response = execute(request)

        then:
        HttpStatus.OK == response.status()
        response.headers.names().stream().filter { it.startsWith("Access-Control-Allow-") }.toList().isEmpty()
    }

    @Property(name = "micronaut.server.cors.configurations.foo.allowedOrigins", value = "http://www.foo.com")
    void "request with origin and no matching configuration"() {
        given:
        HttpRequest request = HttpRequest.create(HttpMethod.OPTIONS, "/example").header(HttpHeaders.ORIGIN, 'http://www.bar.com')

        when:
        HttpResponse<?> response = execute(request)

        then: "the request is passed through because no configuration matches the origin"
        HttpStatus.OK == response.status()
        response.headers.names().stream().filter { it.startsWith("Access-Control-Allow-") }.toList().isEmpty()
    }

    @Unroll
    void "regex matching configuration"(String regex, String origin) {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                "micronaut.server.cors.enabled": "true",
                "micronaut.server.dispatch-options-requests": "true",
                "micronaut.server.cors.configurations.foo.allowedOrigins": origin,
                "micronaut.server.cors.configurations.foo.allowedOriginsRegex": "regex"
        ])
        def client = embeddedServer.applicationContext.getBean(HttpClient)
        HttpRequest request = HttpRequest.create(HttpMethod.OPTIONS, embeddedServer.URL.toString() + "/example").header(HttpHeaders.ORIGIN, origin)

        when:
        HttpResponse<?> response = client.toBlocking().exchange(request)

        then:
        HttpStatus.OK == response.status()
        response.headers.names().size() == 7
        response.headers.contains(HttpHeaders.ALLOW)
        response.headers.contains(HttpHeaders.DATE)
        response.headers.contains(HttpHeaders.CONTENT_TYPE)
        response.headers.contains(HttpHeaders.CONTENT_LENGTH)
        response.headers.find { it.key == 'Access-Control-Allow-Origin' }
        response.headers.find { it.key == 'Vary' }
        response.headers.find { it.key == 'Access-Control-Allow-Credentials' }
        response.headers.find { it.key == 'Access-Control-Allow-Origin' }.value == [origin]
        response.headers.find { it.key == 'Vary' }.value == ['Origin']
        response.headers.find { it.key == 'Access-Control-Allow-Credentials' }.value == [StringUtils.TRUE]

        cleanup:
        embeddedServer.close()

        where:
        regex                               | origin
        '.*'                                | 'http://www.bar.com'
        '^http://www\\.(foo|bar)\\.com$'    | 'http://www.bar.com'
        '^http://www\\.(foo|bar)\\.com$'    | 'http://www.foo.com'
        '.*(bar|foo)$'                      | 'asdfasdf foo'
        '.*(bar|foo)$'                      | 'asdfasdf bar'
        '.*(bar|foo)$'                      | 'http://asdfasdf.foo'
        '.*(bar|foo)$'                      | 'http://asdfasdf.bar'
    }

    @Property(name = "micronaut.server.cors.configurations.foo.allowedOrigins", value = "http://www.foo.com")
    @Property(name = "micronaut.server.cors.configurations.foo.allowedMethods", value = "GET")
    void "test handleRequest with disallowed method"() {
        given:
        HttpRequest request = HttpRequest.create(HttpMethod.OPTIONS, "/example").header(HttpHeaders.ORIGIN, 'http://www.foo.com')

        when:
        HttpResponse<?> response = execute(request)

        then:
        HttpStatus.FORBIDDEN == response.status()
        response.headers.size() == 1
        response.headers.contains(HttpHeaders.CONTENT_LENGTH)
    }

    @Property(name = "micronaut.server.cors.configurations.foo.allowedOrigins", value = "http://www.foo.com")
    @Property(name = "micronaut.server.cors.configurations.foo.allowedMethods", value = "GET")
    void "with disallowed header (not preflight) the request is passed through because allowed headers are only checked for preflight requests"() {
        given:
        HttpRequest request = HttpRequest.create(HttpMethod.GET, "/example").header(HttpHeaders.ORIGIN, 'http://www.foo.com')

        when:
        HttpResponse<?> response = execute(request)

        then:
        HttpStatus.OK == response.status()
        response.headers.names().size() == 6
        response.headers.contains(HttpHeaders.DATE)
        response.headers.contains(HttpHeaders.CONTENT_TYPE)
        response.headers.contains(HttpHeaders.CONTENT_LENGTH)
        response.headers.find { it.key == 'Access-Control-Allow-Origin' }
        response.headers.find { it.key == 'Vary' }
        response.headers.find { it.key == 'Access-Control-Allow-Credentials' }
        response.headers.find { it.key == 'Access-Control-Allow-Origin' }.value == ['http://www.foo.com']
        response.headers.find { it.key == 'Vary' }.value == ['Origin']
        response.headers.find { it.key == 'Access-Control-Allow-Credentials' }.value == [StringUtils.TRUE]
    }

    @Property(name = "micronaut.server.cors.configurations.foo.allowedOrigins", value = "http://www.foo.com")
    @Property(name = "micronaut.server.cors.configurations.foo.allowedMethods", value = "GET")
    @Property(name = "micronaut.server.cors.configurations.foo.allowedHeaders", value = "foo")
    void "test preflight handleRequest with disallowed header"() {
        given:
        HttpRequest request = HttpRequest.create(HttpMethod.OPTIONS, "/example")
                .header(HttpHeaders.ORIGIN, 'http://www.foo.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'GET')
                .header('foo', 'bar')

        when:
        HttpResponse<?> response = execute(request)

        then: "the request is rejected because bar is not allowed"
        HttpStatus.FORBIDDEN == response.status()
    }

    @Property(name = "micronaut.server.cors.configurations.foo.allowedOrigins", value = "http://www.foo.com")
    @Property(name = "micronaut.server.cors.configurations.foo.allowedMethods", value = "GET")
    @Property(name = "micronaut.server.cors.configurations.foo.allowedHeaders", value = "foo,bar")
    void "test preflight with allowed header"() {
        given:
        HttpRequest request = HttpRequest.create(HttpMethod.OPTIONS, "/example")
                .header(HttpHeaders.ORIGIN, 'http://www.foo.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'GET')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, 'foo')

        when:
        HttpResponse<?> response = execute(request)

        then:
        HttpStatus.OK == response.status()
        response.headers.names().size() == 7
        response.headers.contains(HttpHeaders.CONTENT_LENGTH)
        response.headers.find { it.key == 'Access-Control-Allow-Origin' }
        response.headers.find { it.key == 'Vary' }
        response.headers.find { it.key == 'Access-Control-Allow-Credentials' }
        response.headers.find { it.key == 'Access-Control-Allow-Methods' }
        response.headers.find { it.key == 'Access-Control-Allow-Headers' }
        response.headers.find { it.key == 'Access-Control-Max-Age' }
        response.headers.find { it.key == 'Access-Control-Allow-Origin' }.value == ['http://www.foo.com']
        response.headers.find { it.key == 'Vary' }.value == ['Origin']
        response.headers.find { it.key == 'Access-Control-Allow-Credentials' }.value == [StringUtils.TRUE]
        response.headers.find { it.key == 'Access-Control-Allow-Methods' }.value == ['GET']
        response.headers.find { it.key == 'Access-Control-Allow-Headers' }.value == ['foo']
        response.headers.find { it.key == 'Access-Control-Max-Age' }.value == ['1800']
    }

    @Property(name = "micronaut.server.cors.configurations.foo.allowedOrigins", value = "http://www.foo.com")
    void "test handleResponse when configuration not present"() {
        given:
        HttpRequest request = HttpRequest.create(HttpMethod.OPTIONS, "/example")
                .header(HttpHeaders.ORIGIN, 'http://www.bar.com')

        when:
        HttpResponse<?> response = execute(request)

        then:
        HttpStatus.OK == response.status()

        and:
        !response.getHeaders().get(ACCESS_CONTROL_ALLOW_ORIGIN)
        !response.getHeaders().get(VARY)
        !response.getHeaders().getAll(ACCESS_CONTROL_EXPOSE_HEADERS)
        !response.getHeaders().get(ACCESS_CONTROL_ALLOW_CREDENTIALS)
        !response.getHeaders().get(ACCESS_CONTROL_MAX_AGE)
    }

    @Property(name = "micronaut.server.cors.configurations.foo.allowedOrigins", value = "http://www.foo.com")
    @Property(name = "micronaut.server.cors.configurations.foo.exposedHeaders", value = "Foo-Header,Bar-Header")
    void "verify behaviour for normal request"() {
        given:
        HttpRequest request = HttpRequest.create(HttpMethod.OPTIONS, "/example")
                .header(HttpHeaders.ORIGIN, 'http://www.foo.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'GET')
                .header('foo', 'bar')

        when:
        HttpResponse<?> response = execute(request)

        then:
        HttpStatus.OK == response.status()
        response.headers.names().size() == 7
        response.headers.contains(HttpHeaders.CONTENT_LENGTH)
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_ORIGIN) == 'http://www.foo.com' // The origin is echo'd
        response.getHeaders().get(VARY) == 'Origin' // The vary header is set
        response.getHeaders().getAll(ACCESS_CONTROL_EXPOSE_HEADERS) == ['Foo-Header', 'Bar-Header' ]// Expose headers are set from config
        response.getHeaders().get(ACCESS_CONTROL_ALLOW_CREDENTIALS) == 'true' // Allow credentials header is set
        response.getHeaders().get(ACCESS_CONTROL_MAX_AGE) == '1800'
    }

    @Property(name = "micronaut.server.cors.configurations.foo.allowedOrigins", value = "http://www.foo.com")
    @Property(name = "micronaut.server.cors.configurations.foo.exposedHeaders", value = "Foo-Header,Bar-Header")
    void "test handleResponse for preflight request"() {
        given:
        HttpRequest request = HttpRequest.create(HttpMethod.OPTIONS, "/example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, 'X-Header')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, 'Y-Header')
                .header(HttpHeaders.ORIGIN, 'http://www.foo.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'GET')
                .header('foo', 'bar')

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

    @Property(name = "micronaut.server.cors.singleHeader", value = "true")
    @Property(name = "micronaut.server.cors.configurations.foo.exposedHeaders", value = "Foo-Header,Bar-Header")
    void "test handleResponse for preflight request with single header"() {
        given:
        HttpRequest request = HttpRequest.create(HttpMethod.OPTIONS, "/example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, 'X-Header')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, 'Y-Header')
                .header(HttpHeaders.ORIGIN, 'http://www.foo.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'GET')
                .header('foo', 'bar')

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

    @Property(name = "micronaut.server.cors.configurations.foo.allowedOrigins", value = "http://www.foo.com")
    @Property(name = "micronaut.server.cors.configurations.foo.allowedMethods", value = "GET")
    void "test preflight handleRequest on route that doesn't exists"() {
        given:
        HttpRequest request = HttpRequest.create(HttpMethod.OPTIONS, "/doesnt-exists-route")
                .header(HttpHeaders.ORIGIN, 'http://www.foo.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'GET')

        when:
        HttpResponse<?> response = execute(request)

        then:
        HttpStatus.OK == response.status()
    }

    @Property(name = "micronaut.server.cors.configurations.foo.allowedOrigins", value = "http://www.foo.com")
    @Property(name = "micronaut.server.cors.configurations.foo.exposedHeaders", value = "Foo-Header,Bar-Header")
    void "test preflight handleRequest on route that does exist but doesn't handle requested HTTP Method"() {
        given:
        HttpRequest request = HttpRequest.create(HttpMethod.OPTIONS, "/example")
                .header(HttpHeaders.ORIGIN, 'http://www.foo.com')
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, 'POST')

        when:
        HttpResponse<?> response = execute(request)

        then:
        HttpStatus.FORBIDDEN == response.status()
    }

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

    @Singleton
    @Primary
    HttpHostResolver testHttpHostResolver() {
        return new HttpHostResolver() {
            @Override
            String resolve(@Nullable HttpRequest request) {
                return "http://micronautexample.com";
            }
        }
    }

}
