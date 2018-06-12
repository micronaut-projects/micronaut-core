/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.server.netty.AbstractMicronautSpec
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
        def response = rxClient.exchange("/java/response/$action", String).onErrorReturn({ t -> t.response }).blockingFirst()

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
        "ok"                  | HttpStatus.OK                 | null                       | [connection: 'close']
        "ok-with-body"        | HttpStatus.OK                 | "some text"                | ['content-length': '9', 'content-type': 'text/plain'] + [connection: 'close']
        "ok-with-body-object" | HttpStatus.OK                 | '{"name":"blah","age":10}' | defaultHeaders + ['content-length': '24', 'content-type': 'application/json'] + [connection: 'close']
        "status"              | HttpStatus.MOVED_PERMANENTLY  | null                       | [connection: 'close']
        "created-body"        | HttpStatus.CREATED            | '{"name":"blah","age":10}' | defaultHeaders + ['content-length': '24', 'content-type': 'application/json'] + [connection: 'close']
        "created-uri"         | HttpStatus.CREATED            | null                       | [connection: 'close', 'location': 'http://test.com']
        "accepted"            | HttpStatus.ACCEPTED           | null                       | [connection: 'close']
        "disallow"            | HttpStatus.METHOD_NOT_ALLOWED | null                       | [connection: "close", 'allow': 'DELETE']

    }

    @Unroll
    void "test custom HTTP response for action #action"() {
        when:
        def response = rxClient.exchange("/java/response/$action", String).onErrorReturn({ t -> t.response }).blockingFirst()

        def actualHeaders = [:]
        for (name in response.headers.names()) {
            actualHeaders.put(name.toLowerCase(), response.header(name))
        }
        def responseBody = response.body.orElse(null)

        def defaultHeaders = [connection: 'close']

        then:
        response.code() == status.code
        body == null || responseBody == body
        actualHeaders == headers

        where:
        action                | status                       | body                       | headers
        "ok"                  | HttpStatus.OK                | null                       | [connection: 'close']
        "ok-with-body"        | HttpStatus.OK                | "some text"                | ['content-length': '9', 'content-type': 'text/plain'] + [connection: 'close']
        "ok-with-body-object" | HttpStatus.OK                | '{"name":"blah","age":10}' | defaultHeaders + ['content-length': '24', 'content-type': 'application/json'] + [connection: 'close']
        "status"              | HttpStatus.MOVED_PERMANENTLY | null                       | [connection: 'close']
        "created-body"        | HttpStatus.CREATED           | '{"name":"blah","age":10}' | defaultHeaders + ['content-length': '24', 'content-type': 'application/json'] + [connection: 'close']
        "created-uri"         | HttpStatus.CREATED           | null                       | [connection: 'close', 'location': 'http://test.com']
        "accepted"            | HttpStatus.ACCEPTED          | null                       | [connection: 'close']
    }

    void "test content encoding"() {
        when:
        def response = rxClient.exchange(HttpRequest.GET("/java/response/ok-with-body").header("Accept-Encoding", "gzip"), String).onErrorReturn({ t -> t.response }).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == "some text" //decoded by the client
        response.header("Content-Length") == "9" // changed by the decoder
        response.header("Content-Encoding") == null // removed by the decoder
    }

    void "test custom headers"() {
        when:
        def response = rxClient.exchange(HttpRequest.GET("/java/response/custom-headers")).onErrorReturn({ t -> t.response }).blockingFirst()
        Set<String> headers = response.headers.names()

        then: // The content length header was replaced, not appended
        !headers.contains("content-type")
        !headers.contains("Content-Length")
        headers.contains("content-length")
        response.header("Content-Type") == "text/plain"
        response.header("Content-Length") == "3"
    }

   /* @Controller
    static class ResponseController {

        @Get
        HttpResponse accepted() {
            HttpResponse.accepted()
        }

        @Get
        HttpResponse createdUri() {
            HttpResponse.created(new URI("http://test.com"))
        }

        @Get
        HttpResponse createdBody() {
            HttpResponse.created(new Foo(name: "blah", age: 10))
        }

        @Get
        HttpResponse ok() {
            HttpResponse.ok()
        }

        @Get(produces = MediaType.TEXT_PLAIN)
        HttpResponse okWithBody() {
            HttpResponse.ok("some text")
        }

        @Get
        HttpResponse<Foo> okWithBodyObject() {
            HttpResponse.ok(new Foo(name: "blah", age: 10))
                    .headers {
                it.contentType(MediaType.APPLICATION_JSON_TYPE)
            }
        }

        @Get
        HttpMessage status() {
            HttpResponse.status(HttpStatus.MOVED_PERMANENTLY)
        }


    }*/

    static class Foo {
        String name
        Integer age
    }
}
