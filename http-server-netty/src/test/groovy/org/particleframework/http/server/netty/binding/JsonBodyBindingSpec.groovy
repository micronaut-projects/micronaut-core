package org.particleframework.http.server.netty.binding

import com.fasterxml.jackson.core.JsonParseException
import groovy.json.JsonSlurper
import io.reactivex.Flowable
import io.reactivex.schedulers.Schedulers
import org.particleframework.http.HttpHeaders
import org.particleframework.http.HttpRequest
import org.particleframework.http.HttpResponse
import org.particleframework.http.HttpStatus
import org.particleframework.http.annotation.Body
import org.particleframework.http.annotation.Error
import org.particleframework.http.client.exceptions.HttpClientResponseException
import org.particleframework.http.hateos.Link
import org.particleframework.http.hateos.VndError
import org.particleframework.http.server.netty.AbstractParticleSpec
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Post
import org.reactivestreams.Publisher

import java.util.concurrent.CompletableFuture

/**
 * Created by graemerocher on 25/08/2017.
 */
class JsonBodyBindingSpec extends AbstractParticleSpec {
    void "test simple string-based body parsing with incomplete JSON"() {
        when:
        def json = '{"title":"The Stand"'
        rxClient.exchange(
                HttpRequest.POST('/json/string', json), String
        ).blockingFirst()


        then:
        def e = thrown(HttpClientResponseException)
        e.message == "No!! Invalid JSON"
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
        e.message == "No!! Invalid JSON"
        e.response.status == HttpStatus.BAD_REQUEST

        when:
        def response = e.response
        def body = e.response.getBody(String).orElse(null)
        def result = new JsonSlurper().parseText(body)

        then:
        response.code() == HttpStatus.BAD_REQUEST.code
        response.headers.get(HttpHeaders.CONTENT_TYPE) == org.particleframework.http.MediaType.APPLICATION_VND_ERROR
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
                HttpRequest.POST('/json/map', json).contentType(org.particleframework.http.MediaType.APPLICATION_ATOM_XML_TYPE), String
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
                HttpRequest.POST('/json/objectToObject', json), String
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
                HttpRequest.POST('/json/arrayToArray', json), String
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
                HttpRequest.POST('/json/futureMap', json), String
        ).blockingFirst()

        then:
        response.body() == "Body: [name:Fred, age:10]".toString()
    }

    void "test future argument handling with POGO"() {
        when:
        def json = '{"name":"Fred","age":10}'
        def response = rxClient.exchange(
                HttpRequest.POST('/json/futureObject', json), String
        ).blockingFirst()

        then:
        response.body() == "Body: Foo(Fred, 10)".toString()
    }

    void "test publisher argument handling with POGO"() {

        when:
        def json = '{"name":"Fred","age":10}'
        def response = rxClient.exchange(
                HttpRequest.POST('/json/publisherObject', json), String
        ).blockingFirst()

        then:
        response.body() == "Foo(Fred, 10)".toString()
    }

    @Controller(produces = org.particleframework.http.MediaType.APPLICATION_JSON)
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
            def error = new VndError("Invalid JSON: ${jsonParseException.message}")
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
