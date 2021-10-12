package io.micronaut.http.server.netty.binding

import io.micronaut.core.async.annotation.SingleResult
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.core.JsonParseException
import groovy.json.JsonSlurper
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.hateoas.Link
import io.micronaut.http.server.netty.AbstractMicronautSpec
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import spock.lang.Issue

import java.util.concurrent.CompletableFuture

class JsonBodyBindingSpec extends AbstractMicronautSpec {

    void "test JSON is not parsed when the body is a raw body type"() {
        when:
        String json = '{"title":"The Stand"'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/string', json), String
        )).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == 'Body: {"title":"The Stand"'
    }

    void "test JSON is not parsed when the body is a raw body type in a request argument"() {
        when:
        String json = '{"title":"The Stand"'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/request-string', json), String
        )).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == 'Body: {"title":"The Stand"'
    }

    void "test parse body into parameters if no @Body specified"() {
        when:
        String json = '{"name":"Fred", "age":10}'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/params', json), String
        )).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == "Body: Foo(Fred, 10)"
    }

    void "test map-based body parsing with invalid JSON"() {

        when:
        String json = '{"title":The Stand}'
        Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/map', json), String
        )).blockFirst()

        then:
        HttpClientResponseException e = thrown()
        e.message == """Invalid JSON: Unexpected character ('T' (code 84)): expected a valid value (JSON String, Number, Array, Object or token 'null', 'true' or 'false')
 at [Source: UNKNOWN; line: 1, column: 11]"""
        e.response.status == HttpStatus.BAD_REQUEST

        when:
        HttpResponse<?> response = e.response
        String body = e.response.getBody(String).orElse(null)
        def result = new JsonSlurper().parseText(body)

        then:
        response.code() == HttpStatus.BAD_REQUEST.code
        response.headers.get(HttpHeaders.CONTENT_TYPE) == io.micronaut.http.MediaType.APPLICATION_JSON
        result['_links'].self.href == '/json/map'
        result.message.startsWith('Invalid JSON')
    }

    void "test simple map body parsing"() {
        when:
        String json = '{"title":"The Stand"}'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/map', json), String
        )).blockFirst()

        then:
        response.body() == "Body: [title:The Stand]"
    }

    void "test simple string-based body parsing"() {
        when:
        String json = '{"title":"The Stand"}'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/string', json), String
        )).blockFirst()

        then:
        response.body() == "Body: $json"
    }

    void "test binding to part of body with @Body(name)"() {
        when:
        String json = '{"title":"The Stand"}'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/body-title', json), String
        )).blockFirst()

        then:
        response.body() == "Body Title: The Stand"
    }

    void  "test simple string-based body parsing with request argument"() {
        when:
        String json = '{"title":"The Stand"}'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/request-string', json), String
        )).blockFirst()

        then:
        response.body() == "Body: $json"
    }

    void "test simple string-based body parsing with invalid mime type"() {
        when:
        String json = '{"title":"The Stand"}'
        Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/map', json).contentType(io.micronaut.http.MediaType.APPLICATION_ATOM_XML_TYPE), String
        )).blockFirst()

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.UNSUPPORTED_MEDIA_TYPE
    }

    void "test simple POGO body parsing"() {
        when:
        String json = '{"name":"Fred", "age":10}'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/object', json), String
        )).blockFirst()

        then:
        response.body() == "Body: Foo(Fred, 10)"
    }

    void "test simple POGO body parse and return"() {
        when:
        String json = '{"name":"Fred","age":10}'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/object-to-object', json), String
        )).blockFirst()

        then:
        response.body() == json
    }

    void "test array POGO body parsing"() {
        when:
        String json = '[{"name":"Fred", "age":10},{"name":"Barney", "age":11}]'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/array', json), String
        )).blockFirst()

        then:
        response.body() == "Body: Foo(Fred, 10),Foo(Barney, 11)"
    }

    void "test array POGO body parsing and return"() {
        when:
        String json = '[{"name":"Fred","age":10},{"name":"Barney","age":11}]'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/array-to-array', json), String
        )).blockFirst()

        then:
        response.body() == json
    }

    void "test list POGO body parsing"() {
        when:
        String json = '[{"name":"Fred", "age":10},{"name":"Barney", "age":11}]'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/list', json), String
        )).blockFirst()

        then:
        response.body() == "Body: Foo(Fred, 10),Foo(Barney, 11)"
    }

    void "test future argument handling with string"() {
        when:
        String json = '{"name":"Fred","age":10}'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/future', json), String
        )).blockFirst()

        then:
        response.body() == "Body: $json".toString()
    }

    void "test future argument handling with map"() {
        when:
        String json = '{"name":"Fred","age":10}'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/future-map', json), String
        )).blockFirst()

        then:
        response.body() == "Body: [name:Fred, age:10]".toString()
    }

    void "test future argument handling with POGO"() {
        when:
        String json = '{"name":"Fred","age":10}'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/future-object', json), String
        )).blockFirst()

        then:
        response.body() == "Body: Foo(Fred, 10)".toString()
    }

    void "test publisher argument handling with POGO"() {
        when:
        String json = '{"name":"Fred","age":10}'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/publisher-object', json), String
        )).blockFirst()

        then:
        response.body() == "[Foo(Fred, 10)]".toString()
    }

    void "test singe argument handling"() {
        when:
        String json = '{"message":"foo"}'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/single', json), String
        )).blockFirst()

        then:
        response.body() == "$json".toString()
    }

    void "test request generic type binding"() {
        when:
        String json = '{"name":"Fred","age":10}'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/request-generic', json), String
        )).blockFirst()

        then:
        response.body() == "Foo(Fred, 10)".toString()
    }

    void "test request generic type no body"() {
        when:
        String json = ''
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/request-generic', json), String
        )).blockFirst()

        then:
        HttpClientResponseException ex = thrown()
        ex.response.code() == HttpStatus.BAD_REQUEST.code
        ex.response.getBody(Map).get()._embedded.errors[0].message.contains("Required argument [HttpRequest request] not specified")
    }

    void "test request generic type conversion error"() {
        when:
        String json = '[1,2,3]'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/request-generic', json), String
        )).blockFirst()

        then:
        response.body() == "not found"
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/5088")
    void "test deserializing a wrapper of list of pojos"() {
        when:
        String json = '[{"name":"Joe"},{"name":"Sally"}]'
        HttpResponse<String> response = Flux.from(rxClient.exchange(
                HttpRequest.POST('/json/deserialize-listwrapper', json), String
        )).blockFirst()

        then:
        response.body() == '["Joe","Sally"]'
    }

    @Controller(value = "/json", produces = io.micronaut.http.MediaType.APPLICATION_JSON)
    static class JsonController {

        @Post("/params")
        String params(String name, int age) {
            "Body: ${new Foo(name: name, age: age)}"
        }

        @Post("/string")
        String string(@Body String text) {
            "Body: ${text}"
        }

        @Post("/body-title")
        String bodyNamed(@Body("title") String text) {
            "Body Title: ${text}"
        }

        @Post("/request-string")
        String requestString(HttpRequest<String> req) {
            "Body: ${req.body.orElse("empty")}"
        }

        @Post("/map")
        String map(@Body Map<String, Object> json) {
            "Body: ${json}"
        }

        @Post("/object")
        String object(@Body Foo foo) {
            "Body: $foo"
        }

        @Post("/object-to-object")
        Foo objectToObject(@Body Foo foo) {
            return foo
        }

        @Post("/array") array(@Body Foo[] foos) {
            "Body: ${foos.join(',')}"
        }

        @Post("/array-to-array") arrayToArray(@Body Foo[] foos) {
            return foos
        }

        @Post("/list") list(@Body List<Foo> foos) {
            "Body: ${foos.join(',')}"
        }

        @Post("/nested")
        String nested(@Body('foo') Foo foo) {
            "Body: $foo"
        }

        @Post("/single")
        @SingleResult
        Publisher<String> single(@Body Publisher<String> message) {
            message
        }

        @Post("/future")
        CompletableFuture<String> future(@Body CompletableFuture<String> future) {
            future.thenApply({ String json ->
                "Body: $json".toString()
            })
        }

        @Post("/future-map")
        CompletableFuture<String> futureMap(@Body CompletableFuture<Map<String,Object>> future) {
            future.thenApply({ Map<String,Object> json ->
                "Body: $json".toString()
            })
        }


        @Post("/future-object")
        CompletableFuture<String> futureObject(@Body CompletableFuture<Foo> future) {
            future.thenApply({ Foo foo ->
                "Body: $foo".toString()
            })
        }

        @Post("/publisher-object")
        Publisher<String> publisherObject(@Body Publisher<Foo> publisher) {
            return Flux.from(publisher)
                    .subscribeOn(Schedulers.boundedElastic())
                    .map({ Foo foo ->
                        foo.toString()
            })
        }

        @Post("/request-generic")
        String requestGeneric(HttpRequest<Foo> request) {
            return request.getBody().map({ foo -> foo.toString()}).orElse("not found")
        }

        @Post("/deserialize-listwrapper")
        List<String> requestListWrapper(@Body MyReqBody myReqBody) {
            return myReqBody.items*.name
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

    @Introspected
    static class MyReqBody {

        private final List<MyItem> items

        @JsonCreator
        MyReqBody(final List<MyItem> items) {
            this.items = items
        }

        List<MyItem> getItems() {
            items
        }
    }

    @Introspected
    static class MyItem {
        String name
    }
}
