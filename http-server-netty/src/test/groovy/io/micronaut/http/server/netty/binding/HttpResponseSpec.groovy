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
package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMessage
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.Shared
import spock.lang.Unroll

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class HttpResponseSpec extends AbstractMicronautSpec {
    @Shared
            defaultHeaders = [:]

    @Unroll
    void "test custom HTTP response for java action #action"() {

        when:
        HttpResponse<?> response = Flux.from(httpClient.exchange("/java/response/$action", String))
                .onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        def actualHeaders = [:]
        for (name in response.headers.names()) {
            actualHeaders.put(name.toLowerCase(), response.header(name))
        }
        def responseBody = response.body.orElse(null)


        then:
        response.code() == status.code
        body == null || responseBody == body
        actualHeaders == headers


        where:
        action                | status                        | body                       | headers
        "ok"                  | HttpStatus.OK                 | null                       | ['content-length': '0']
        "ok-with-body"        | HttpStatus.OK                 | "some text"                | ['content-length': '9', 'content-type': 'text/plain']
        "error-with-body"     | HttpStatus.INTERNAL_SERVER_ERROR | "some text"             | ['content-length': '9', 'content-type': 'text/plain']
        "ok-with-body-object" | HttpStatus.OK                 | '{"name":"blah","age":10}' | defaultHeaders + ['content-length': '24', 'content-type': 'application/json']
        "status"              | HttpStatus.MOVED_PERMANENTLY  | null                       | ['content-length': '0']
        "created-body"        | HttpStatus.CREATED            | '{"name":"blah","age":10}' | defaultHeaders + ['content-length': '24', 'content-type': 'application/json']
        "created-uri"         | HttpStatus.CREATED            | null                       | ['content-length': '0', 'location': 'http://test.com']
        "created-body-uri"    | HttpStatus.CREATED            | '{"name":"blah","age":10}' | defaultHeaders + ['content-length': '24', 'content-type': 'application/json'] + ['location': 'http://test.com']
        "accepted"            | HttpStatus.ACCEPTED           | null                       | ['content-length': '0']
        "accepted-uri"        | HttpStatus.ACCEPTED           | null                       | ['content-length': '0', 'location': 'http://example.com']
        "disallow"            | HttpStatus.METHOD_NOT_ALLOWED | null                       | ['content-length': '0', 'allow': 'DELETE']
        "optional-response/false" | HttpStatus.OK             | null                       | ['content-length': '0']
        "optional-response/true"  | HttpStatus.NOT_FOUND      | null                       | ['content-type': 'application/json', 'content-length': '162']

    }

    @Unroll
    void "test custom HTTP response for action #action"() {
        when:
        HttpResponse<?> response = Flux.from(httpClient.exchange("/java/response/$action", String))
                .onErrorResume(t -> {
                    if (t instanceof HttpClientResponseException) {
                        return Flux.just(((HttpClientResponseException) t).response)
                    }
                    throw t
                }).blockFirst()

        def actualHeaders = [:]
        for (name in response.headers.names()) {
            actualHeaders.put(name.toLowerCase(), response.header(name))
        }
        def responseBody = response.body.orElse(null)

        def defaultHeaders = [connection: 'keep-alive']

        then:
        response.code() == status.code
        body == null || responseBody == body
        actualHeaders == headers

        where:
        action                | status                       | body                       | headers
        "ok"                  | HttpStatus.OK                | null                       | ['content-length': '0']
        "ok-with-body"        | HttpStatus.OK                | "some text"                | ['content-length': '9', 'content-type': 'text/plain']
        "error-with-body"     | HttpStatus.INTERNAL_SERVER_ERROR | "some text"            | ['content-length': '9', 'content-type': 'text/plain']
        "ok-with-body-object" | HttpStatus.OK                | '{"name":"blah","age":10}' | defaultHeaders + ['content-length': '24', 'content-type': 'application/json']
        "status"              | HttpStatus.MOVED_PERMANENTLY | null                       | ['content-length': '0']
        "created-body"        | HttpStatus.CREATED           | '{"name":"blah","age":10}' | defaultHeaders + ['content-length': '24', 'content-type': 'application/json']
        "created-uri"         | HttpStatus.CREATED           | null                       | ['content-length': '0', 'location': 'http://test.com']
        "accepted"            | HttpStatus.ACCEPTED          | null                       | ['content-length': '0']
        "accepted-uri"        | HttpStatus.ACCEPTED          | null                       | ['content-length': '0', 'location': 'http://example.com']
    }

    void "test content encoding"() {
        when:
        HttpResponse<String> response = Flux.from(httpClient.exchange(HttpRequest.GET("/java/response/ok-with-body").header("Accept-Encoding", "gzip"), String))
                .onErrorResume(t -> {
                    if (t instanceof HttpClientResponseException) {
                        return Flux.just(((HttpClientResponseException) t).response)
                    }
                    throw t
                }).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == "some text" //decoded by the client
        response.header("Content-Length") == "9" // changed by the decoder
        response.header("Content-Encoding") == null // removed by the decoder
    }

    void "test custom headers"() {
        when:
        HttpResponse<?> response = Flux.from(httpClient.exchange(HttpRequest.GET("/java/response/custom-headers")))
                .onErrorResume(t -> {
                    if (t instanceof HttpClientResponseException) {
                        return Flux.just(((HttpClientResponseException) t).response)
                    }
                    throw t
                }).blockFirst()
        HttpHeaders headers = response.headers

        then: // The content length header was replaced, not appended
        response.header("Content-Type") == "text/plain"
        response.header("Content-Length") == "3"
        response.header("content-type") == "text/plain"
        response.header("content-length") == "3"
    }

    void "test server header"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['micronaut.server.server-header': 'Foo!', (SPEC_NAME_PROPERTY):getClass().simpleName])
        def ctx = server.getApplicationContext()
        HttpClient client = ctx.createBean(HttpClient, server.getURL())

        when:
        def resp = client.toBlocking().exchange(HttpRequest.GET('/test-header'))

        then:
        resp.header("Server") == "Foo!"

        cleanup:
        ctx.stop()
        server.stop()
        server.close()
    }

    void "test default server header"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [(SPEC_NAME_PROPERTY):getClass().simpleName])
        def ctx = server.getApplicationContext()
        HttpClient client = ctx.createBean(HttpClient, server.getURL())

        when:
        def resp = client.toBlocking().exchange(HttpRequest.GET('/test-header'))

        then:
        !resp.header("Server")

        cleanup:
        ctx.stop()
        server.stop()
        server.close()
    }

    void "test default date header"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [(SPEC_NAME_PROPERTY):getClass().simpleName])
        ApplicationContext ctx = server.getApplicationContext()
        HttpClient client = ctx.createBean(HttpClient, server.getURL())

        when:
        def resp = client.toBlocking().exchange(HttpRequest.GET('/test-header'))

        then:
        resp.header("Date")

        cleanup:
        ctx.stop()
        server.stop()
        server.close()
    }

    void "test date header turned off"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['micronaut.server.date-header': false, (SPEC_NAME_PROPERTY):getClass().simpleName])
        ApplicationContext ctx = server.getApplicationContext()
        HttpClient client = ctx.createBean(HttpClient, server.getURL())

        when:
        def resp = client.toBlocking().exchange(HttpRequest.GET('/test-header'))

        then:
        !resp.header("Date")

        cleanup:
        ctx.stop()
        server.stop()
        server.close()
    }

    void "test keep alive connection header is not set for 500 response"() {
        when:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.netty.keepAliveOnServerError': false,
                'micronaut.server.date-header': false,
                (SPEC_NAME_PROPERTY):getClass().simpleName,
        ])
        ApplicationContext ctx = server.getApplicationContext()
        HttpClient client = ctx.createBean(HttpClient, server.getURL())

        Flux.from(client.exchange(
          HttpRequest.GET('/test-header/fail')
        )).blockFirst()

        then:
        HttpClientResponseException e = thrown()
        e.response.status == HttpStatus.INTERNAL_SERVER_ERROR
        e.response.header(HttpHeaders.CONNECTION) == 'close'

        cleanup:
        ctx.stop()
        server.stop()
        server.close()
    }

    void "test connection header is defaulted to keep-alive by default for > 499 response"() {
        when:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
          (SPEC_NAME_PROPERTY):getClass().simpleName
        ])
        def ctx = server.getApplicationContext()
        HttpClient client = ctx.createBean(HttpClient, server.getURL())

        Flux.from(client.exchange(
          HttpRequest.GET('/test-header/fail')
        )).blockFirst()

        then:
        HttpClientResponseException e = thrown()
        e.response.status == HttpStatus.INTERNAL_SERVER_ERROR
        // HTTP/1.1 is keep-alive by default
        e.response.header(HttpHeaders.CONNECTION) == null

        cleanup:
        ctx.stop()
        server.stop()
        server.close()
    }

    @Controller('/test-header')
    @Requires(property = 'spec.name', value = 'HttpResponseSpec')
    static class TestController {
        @Get
        HttpStatus index() {
            HttpStatus.OK
        }

        @Get("/fail")
        HttpResponse fail() {
            HttpResponse.serverError("server error")
        }
    }

    static class Foo {
        String name
        Integer age
    }

    @Override
    Map<String, Object> getConfiguration() {
        super.getConfiguration() << ['micronaut.server.date-header': false]
    }

    @Requires(property = 'spec.name', value = 'HttpResponseSpec')
    @Controller("/java/response")
    static class ResponseController {

        @Get("/disallow")
        public HttpResponse disallow() {
            return HttpResponse.notAllowed(HttpMethod.DELETE);
        }

        @Get("/accepted")
        public HttpResponse accepted() {
            return HttpResponse.accepted();
        }

        @Get("/accepted-uri")
        public HttpResponse acceptedUri() {
            return HttpResponse.accepted(HttpResponse.uri("http://example.com"));
        }

        @Get("/created-uri")
        public HttpResponse createdUri() {
            return HttpResponse.created(HttpResponse.uri("http://test.com"));
        }

        @Get("/created-body")
        public HttpResponse createdBody() {
            return HttpResponse.created(new io.micronaut.http.server.netty.java.Foo("blah", 10));
        }

        @Get("/created-body-uri")
        public HttpResponse createdBodyUri() {
            return HttpResponse.created(new io.micronaut.http.server.netty.java.Foo("blah", 10), HttpResponse.uri("http://test.com"));
        }

        @Get("/ok")
        public HttpResponse ok() {
            return HttpResponse.ok();
        }

        @Get(value = "/ok-with-body", produces = MediaType.TEXT_PLAIN)
        public HttpResponse okWithBody() {
            return HttpResponse.ok("some text");
        }

        @Get(value = "/error-with-body", produces = MediaType.TEXT_PLAIN)
        public HttpResponse errorWithBody() {
            return HttpResponse.serverError().body("some text");
        }

        @Get("/ok-with-body-object")
        public HttpResponse<io.micronaut.http.server.netty.java.Foo> okWithBodyObject() {
            return HttpResponse.ok(new io.micronaut.http.server.netty.java.Foo("blah", 10))
                    .headers((headers)->
                            headers.contentType(MediaType.APPLICATION_JSON_TYPE)
                    );
        }

        @Get("/status")
        public HttpMessage status() {
            return HttpResponse.status(HttpStatus.MOVED_PERMANENTLY);
        }

        @Get("/custom-headers")
        public HttpResponse customHeaders() {
            return HttpResponse.ok("abc").contentType("text/plain").contentLength(7);
        }

        @Get("/optional-response/{empty}")
        public Optional<HttpResponse> optionalResponse(Boolean empty) {
            if (empty) {
                return Optional.empty();
            } else {
                return Optional.of(HttpResponse.ok());
            }
        }
    }
}
