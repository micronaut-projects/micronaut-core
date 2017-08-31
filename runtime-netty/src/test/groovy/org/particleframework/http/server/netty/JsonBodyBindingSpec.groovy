package org.particleframework.http.server.netty

import groovy.transform.ToString
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.particleframework.http.binding.annotation.Body
import org.particleframework.stereotype.Controller
import org.particleframework.web.router.annotation.Post

import java.util.concurrent.CompletableFuture

/**
 * Created by graemerocher on 25/08/2017.
 */
class JsonBodyBindingSpec extends AbstractParticleSpec {


    void "test simple string-based body parsing"() {

        when:
        def json = '{"title:"The Stand"}'
        def request = new Request.Builder()
                .url("$server/json/string")
                .header("Content-Length", json.length().toString())
                .post(RequestBody.create(MediaType.parse("application/json"), json))

        then:
        client.newCall(
                request.build()
        ).execute().body().string() == "Body: $json"

    }


    @Controller
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
    }

    @ToString
    static class Foo {
        String name
        Integer age
    }
}
