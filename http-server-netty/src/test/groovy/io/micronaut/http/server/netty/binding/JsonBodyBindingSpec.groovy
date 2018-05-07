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

import com.fasterxml.jackson.core.JsonParseException
import groovy.json.JsonSlurper
import io.reactivex.Flowable
import io.reactivex.schedulers.Schedulers
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Error
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.hateos.Link
import io.micronaut.http.hateos.JsonError
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import org.reactivestreams.Publisher

import java.util.concurrent.CompletableFuture

/**
 * Created by graemerocher on 25/08/2017.
 */
class JsonBodyBindingSpec extends AbstractMicronautSpec {
    void "test simple string-based body parsing with incomplete JSON"() {
        when:
        def json = '{"title":"The Stand"'
        rxClient.exchange(
                HttpRequest.POST('/json/string', json), String
        ).blockingFirst()


        then:
        def e = thrown(HttpClientResponseException)
        e.message == """Invalid JSON: Unexpected end-of-input
 at [Source: UNKNOWN; line: 1, column: 21]"""
        e.response.status == HttpStatus.BAD_REQUEST

        when:
        def body = e.response.getBody(String).orElse(null)
        def result = new JsonSlurper().parseText(body)



        then:
        result['_links'].self.href == '/json/string'
        result.message.startsWith('Invalid JSON')
    }

    void "test parse body into parameters if no @Body specified"() {
        when:
        def json = '{"name":"Fred", "age":10}'
        def response = rxClient.exchange(
                HttpRequest.POST('/json/params', json), String
        ).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == "Body: Foo(Fred, 10)"
    }

    void "test simple string-based body parsing with invalid JSON"() {

        when:
        def json = '{"title":The Stand}'
        rxClient.exchange(
                HttpRequest.POST('/json/string', json), String
        ).blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == """Invalid JSON: Unexpected character ('T' (code 84)): expected a valid value (number, String, array, object, 'true', 'false' or 'null')
 at [Source: UNKNOWN; line: 1, column: 11]"""
        e.response.status == HttpStatus.BAD_REQUEST

        when:
        def response = e.response
        def body = e.response.getBody(String).orElse(null)
        def result = new JsonSlurper().parseText(body)

        then:
        response.code() == HttpStatus.BAD_REQUEST.code
        response.headers.get(HttpHeaders.CONTENT_TYPE) == io.micronaut.http.MediaType.APPLICATION_JSON
        result['_links'].self.href == '/json/string'
        result.message.startsWith('Invalid JSON')
    }



    void "test simple map body parsing"() {

        when:
        def json = '{"title":"The Stand"}'
        def response = rxClient.exchange(
                HttpRequest.POST('/json/map', json), String
        ).blockingFirst()

        then:
        response.body() == "Body: [title:The Stand]"
    }


    void  "test simple string-based body parsing"() {

        when:
        def json = '{"title":"The Stand"}'
        def response = rxClient.exchange(
                HttpRequest.POST('/json/string', json), String
        ).blockingFirst()

        then:
        response.body() == "Body: $json"

    }

    void "test simple string-based body parsing with invalid mime type"() {

        when:
        def json = '{"title":"The Stand"}'
        rxClient.exchange(
                HttpRequest.POST('/json/map', json).contentType(io.micronaut.http.MediaType.APPLICATION_ATOM_XML_TYPE), String
        ).blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNSUPPORTED_MEDIA_TYPE
    }



    void "test simple POGO body parsing"() {
        when:
        def json = '{"name":"Fred", "age":10}'
        def response = rxClient.exchange(
                HttpRequest.POST('/json/object', json), String
        ).blockingFirst()

        then:
        response.body() == "Body: Foo(Fred, 10)"
    }

    void "test simple POGO body parse and return"() {
        when:
        def json = '{"name":"Fred","age":10}'
        def response = rxClient.exchange(
                HttpRequest.POST('/json/object-to-object', json), String
        ).blockingFirst()


        then:
        response.body() == json
    }


    void "test array POGO body parsing"() {
        when:
        def json = '[{"name":"Fred", "age":10},{"name":"Barney", "age":11}]'
        def response = rxClient.exchange(
                HttpRequest.POST('/json/array', json), String
        ).blockingFirst()

        then:
        response.body() == "Body: Foo(Fred, 10),Foo(Barney, 11)"
    }

    void "test array POGO body parsing and return"() {
        when:
        def json = '[{"name":"Fred","age":10},{"name":"Barney","age":11}]'
        def response = rxClient.exchange(
                HttpRequest.POST('/json/array-to-array', json), String
        ).blockingFirst()

        then:
        response.body() == json
    }

    void "test list POGO body parsing"() {

        when:
        def json = '[{"name":"Fred", "age":10},{"name":"Barney", "age":11}]'
        def response = rxClient.exchange(
                HttpRequest.POST('/json/list', json), String
        ).blockingFirst()

        then:
        response.body() == "Body: Foo(Fred, 10),Foo(Barney, 11)"
    }

    void "test future argument handling with string"() {
        when:
        def json = '{"name":"Fred","age":10}'
        def response = rxClient.exchange(
                HttpRequest.POST('/json/future', json), String
        ).blockingFirst()



        then:
        response.body() == "Body: $json".toString()
    }

    void "test future argument handling with map"() {
        when:
        def json = '{"name":"Fred","age":10}'
        def response = rxClient.exchange(
                HttpRequest.POST('/json/future-map', json), String
        ).blockingFirst()

        then:
        response.body() == "Body: [name:Fred, age:10]".toString()
    }

    void "test future argument handling with POGO"() {
        when:
        def json = '{"name":"Fred","age":10}'
        def response = rxClient.exchange(
                HttpRequest.POST('/json/future-object', json), String
        ).blockingFirst()

        then:
        response.body() == "Body: Foo(Fred, 10)".toString()
    }

    void "test publisher argument handling with POGO"() {

        when:
        def json = '{"name":"Fred","age":10}'
        def response = rxClient.exchange(
                HttpRequest.POST('/json/publisher-object', json), String
        ).blockingFirst()

        then:
        response.body() == "[Foo(Fred, 10)]".toString()
    }

    @Controller(produces = io.micronaut.http.MediaType.APPLICATION_JSON)
    static class JsonController {

        @Post
        String params(String name, int age) {
            "Body: ${new Foo(name: name, age: age)}"
        }

        @Post
        String string(@Body String text) {
            "Body: ${text}"
        }

        @Post
        String map(@Body Map<String, Object> json) {
            "Body: ${json}"
        }

        @Post
        String object(@Body Foo foo) {
            "Body: $foo"
        }

        @Post
        Foo objectToObject(@Body Foo foo) {
            return foo
        }

        @Post array(@Body Foo[] foos) {
            "Body: ${foos.join(',')}"
        }

        @Post arrayToArray(@Body Foo[] foos) {
            return foos
        }

        @Post list(@Body List<Foo> foos) {
            "Body: ${foos.join(',')}"
        }

        @Post
        String nested(@Body('foo') Foo foo) {
            "Body: $foo"
        }

        @Post
        CompletableFuture<String> future(@Body CompletableFuture<String> future) {
            future.thenApply({ String json ->
                "Body: $json".toString()
            })
        }

        @Post
        CompletableFuture<String> futureMap(@Body CompletableFuture<Map<String,Object>> future) {
            future.thenApply({ Map<String,Object> json ->
                "Body: $json".toString()
            })
        }


        @Post
        CompletableFuture<String> futureObject(@Body CompletableFuture<Foo> future) {
            future.thenApply({ Foo foo ->
                "Body: $foo".toString()
            })
        }

        @Post
        Publisher<String> publisherObject(@Body Flowable<Foo> publisher) {
            return publisher
                    .subscribeOn(Schedulers.io())
                    .map({ Foo foo ->
                        foo.toString()
            })
        }


        @Error(JsonParseException)
        HttpResponse jsonError(HttpRequest request, JsonParseException jsonParseException) {
            def response = HttpResponse.status(HttpStatus.BAD_REQUEST, "No!! Invalid JSON")
            def error = new JsonError("Invalid JSON: ${jsonParseException.message}")
            error.link(Link.SELF, Link.of(request.getUri()))
            response.body(error)
            return response
        }
    }

    static class Foo {
        String name
        Integer age

        @Override
        String toString() {
            "Foo($name, $age)"
        }
    }
}
