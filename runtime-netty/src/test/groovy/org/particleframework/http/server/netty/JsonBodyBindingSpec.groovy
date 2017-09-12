package org.particleframework.http.server.netty

import com.fasterxml.jackson.core.JsonParseException
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.particleframework.http.HttpResponse
import org.particleframework.http.HttpStatus
import org.particleframework.http.binding.annotation.Body
import org.particleframework.stereotype.Controller
import org.particleframework.web.router.annotation.Post
import spock.lang.Ignore

import java.util.concurrent.CompletableFuture

/**
 * Created by graemerocher on 25/08/2017.
 */
class JsonBodyBindingSpec extends AbstractParticleSpec {


    void "test simple string-based body parsing"() {

        when:
        def json = '{"title":"The Stand"}'
        def request = new Request.Builder()
                .url("$server/json/string")
                .header("Content-Length", json.length().toString())
                .post(RequestBody.create(MediaType.parse("application/json"), json))

        then:
        client.newCall(
                request.build()
        ).execute().body().string() == "Body: $json"

    }

    void "test simple string-based body parsing with invalid mime type"() {

        when:
        def json = '{"title":The Stand}'
        def request = new Request.Builder()
                .url("$server/json/string")
                .header("Content-Length", json.length().toString())
                .post(RequestBody.create(MediaType.parse("application/xml"), json))

        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.UNSUPPORTED_MEDIA_TYPE.code

    }


    void "test simple string-based body parsing with invalid JSON"() {

        when:
        def json = '{"title":The Stand}'
        def request = new Request.Builder()
                .url("$server/json/string")
                .header("Content-Length", json.length().toString())
                .post(RequestBody.create(MediaType.parse("application/json"), json))

        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.BAD_REQUEST.code
        response.message() == "No!! Invalid JSON"

    }

    void "test simple string-based body parsing with incomplete JSON"() {

        when:
        def json = '{"title":"The Stand"'
        def request = new Request.Builder()
                .url("$server/json/string")
                .header("Content-Length", json.length().toString())
                .post(RequestBody.create(MediaType.parse("application/json"), json))

        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.BAD_REQUEST.code
        response.message() == "No!! Invalid JSON"

    }

    void "test simple map body parsing"() {

        when:
        def json = '{"title":"The Stand"}'
        def request = new Request.Builder()
                .url("$server/json/map")
                .header("Content-Length", json.length().toString())
                .post(RequestBody.create(MediaType.parse("application/json"), json))

        then:
        client.newCall(
                request.build()
        ).execute().body().string() == "Body: [title:The Stand]"
    }

    void "test simple POGO body parsing"() {

        when:
        def json = '{"name":"Fred", "age":10}'
        def request = new Request.Builder()
                .url("$server/json/object")
                .header("Content-Length", json.length().toString())
                .post(RequestBody.create(MediaType.parse("application/json"), json))

        then:
        client.newCall(
                request.build()
        ).execute().body().string() == "Body: Foo(Fred, 10)"
    }

    void "test simple POGO body parse and return"() {

        when:
        def json = '{"name":"Fred","age":10}'
        def request = new Request.Builder()
                .url("$server/json/objectToObject")
                .header("Content-Length", json.length().toString())
                .post(RequestBody.create(MediaType.parse("application/json"), json))

        then:
        client.newCall(
                request.build()
        ).execute().body().string() == json
    }


    void "test array POGO body parsing"() {

        when:
        def json = '[{"name":"Fred", "age":10},{"name":"Barney", "age":11}]'
        def request = new Request.Builder()
                .url("$server/json/array")
                .header("Content-Length", json.length().toString())
                .post(RequestBody.create(MediaType.parse("application/json"), json))

        then:
        client.newCall(
                request.build()
        ).execute().body().string() == "Body: Foo(Fred, 10),Foo(Barney, 11)"
    }

    void "test array POGO body parsing and return"() {

        when:
        def json = '[{"name":"Fred","age":10},{"name":"Barney","age":11}]'
        def request = new Request.Builder()
                .url("$server/json/arrayToArray")
                .header("Content-Length", json.length().toString())
                .post(RequestBody.create(MediaType.parse("application/json"), json))

        then:
        client.newCall(
                request.build()
        ).execute().body().string() == json
    }

    void "test list POGO body parsing"() {

        when:
        def json = '[{"name":"Fred", "age":10},{"name":"Barney", "age":11}]'
        def request = new Request.Builder()
                .url("$server/json/list")
                .header("Content-Length", json.length().toString())
                .post(RequestBody.create(MediaType.parse("application/json"), json))

        then:
        client.newCall(
                request.build()
        ).execute().body().string() == "Body: Foo(Fred, 10),Foo(Barney, 11)"
    }

    void "test future argument handling with string"() {

        when:
        def json = '{"name":"Fred","age":10}'
        def request = new Request.Builder()
                .url("$server/json/future")
                .header("Content-Length", json.length().toString())
                .post(RequestBody.create(MediaType.parse("application/json"), json))

        then:
        client.newCall(
                request.build()
        ).execute().body().string() == "Body: $json".toString()
    }

    void "test future argument handling with map"() {

        when:
        def json = '{"name":"Fred","age":10}'
        def request = new Request.Builder()
                .url("$server/json/futureMap")
                .header("Content-Length", json.length().toString())
                .post(RequestBody.create(MediaType.parse("application/json"), json))

        then:
        client.newCall(
                request.build()
        ).execute().body().string() == "Body: [name:Fred, age:10]".toString()
    }

    void "test future argument handling with POGO"() {

        when:
        def json = '{"name":"Fred","age":10}'
        def request = new Request.Builder()
                .url("$server/json/futureObject")
                .header("Content-Length", json.length().toString())
                .post(RequestBody.create(MediaType.parse("application/json"), json))

        then:
        client.newCall(
                request.build()
        ).execute().body().string() == "Body: Foo(Fred, 10)".toString()
    }

    @Controller(produces = org.particleframework.http.MediaType.APPLICATION_JSON)
    static class JsonController {

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

        HttpResponse jsonError(JsonParseException jsonParseException) {
            return HttpResponse.status(HttpStatus.BAD_REQUEST,"No!! Invalid JSON")
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
