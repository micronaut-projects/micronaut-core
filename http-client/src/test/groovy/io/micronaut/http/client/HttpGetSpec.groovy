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
package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import io.reactivex.functions.Consumer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class HttpGetSpec extends Specification {
    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer)

    void "test simple get request"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
        def flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/get/simple").header("Accept-Encoding", "gzip")
        ))
        Optional<String> body = flowable.map({res ->
            res.getBody(String)}
        ).blockingFirst()

        then:
        body.isPresent()
        body.get() == 'success'

        cleanup:
        client.stop()
        client.close()
    }


    void "test simple 404 request"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
        def flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/get/doesntexist")
        ))

        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Page Not Found"
        e.status == HttpStatus.NOT_FOUND

        cleanup:
        client.stop()
        client.close()
    }

    void "test 500 request with body"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
        def flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/get/error")
        ))

        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Server error"
        e.status == HttpStatus.INTERNAL_SERVER_ERROR

        cleanup:
        client.stop()
        client.close()
    }


    void "test 500 request with json body"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
        def flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/get/jsonError")
        ))

        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error"
        e.status == HttpStatus.INTERNAL_SERVER_ERROR

        cleanup:
        client.stop()
        client.close()
    }

    void "test simple 404 request as VndError"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
        def flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/get/doesntexist")
        ))

        def response = flowable.onErrorReturn({ error ->
            if (error instanceof HttpClientResponseException) {
                return HttpResponse.status(error.status).body(error.response.getBody(Map).orElse(null))
            }
            throw error
        }).blockingFirst()

        def body = response.body

        then:
        body.isPresent()
        body.get().message == "Page Not Found"

        cleanup:
        client.stop()
        client.close()
    }

    void "test simple blocking get request"() {

        given:
        def asyncClient = HttpClient.create(embeddedServer.getURL())
        BlockingHttpClient client = asyncClient.toBlocking()

        when:
        HttpResponse<String> response = client.exchange(
                HttpRequest.GET("/get/simple"),
                String
        )

        def body = response.getBody()

        then:
        body.isPresent()
        body.get() == 'success'

        cleanup:
        asyncClient.stop()
        asyncClient.close()
    }

    void "test simple get request with type"() {
        given:
        HttpClient client = new DefaultHttpClient(embeddedServer.getURL())

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/get/simple"), String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody()

        then:
        response.status == HttpStatus.OK
        body.isPresent()
        body.get() == 'success'

        cleanup:
        client.stop()
    }

    void "test simple exchange request with POJO"() {
        given:
        def context = ApplicationContext.run()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        Flowable<HttpResponse<Book>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/get/pojo"), Book
        ))

        HttpResponse<Book> response = flowable.blockingFirst()
        Optional<Book> body = response.getBody()

        then:
        response.contentType.isPresent()
        response.contentType.get() == MediaType.APPLICATION_JSON_TYPE
        response.status == HttpStatus.OK
        body.isPresent()
        body.get().title == 'The Stand'


        cleanup:
        client.stop()

    }

    void "test simple retrieve request with POJO"() {
        given:
        def context = ApplicationContext.run()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        Flowable<Book> flowable = Flowable.fromPublisher(client.retrieve(
                HttpRequest.GET("/get/pojo"), Book
        ))

        Book book = flowable.blockingFirst()

        then:
        book != null
        book.title == "The Stand"


        cleanup:
        client.stop()
    }

    void "test simple get request with POJO list"() {
        given:
        def context = ApplicationContext.run()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        Flowable<HttpResponse<List<Book>>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/get/pojoList"), Argument.of(List, Book)
        ))

        HttpResponse<List<Book>> response = flowable.blockingFirst()
        Optional<List<Book>> body = response.getBody()

        then:
        response.contentType.isPresent()
        response.contentType.get() == MediaType.APPLICATION_JSON_TYPE
        response.status == HttpStatus.OK
        body.isPresent()


        when:
        List<Book> list = body.get()

        then:
        list.size() == 1
        list.get(0) instanceof Book
        list.get(0).title == 'The Stand'


        cleanup:
        client.stop()
    }

    void "test get with @Client"() {
        given:
        MyGetHelper helper = embeddedServer.applicationContext.getBean(MyGetHelper)

        expect:
        helper.simple() == "success"
        helper.simpleSlash() == "success"
        helper.simplePreceedingSlash() == "success"
        helper.simpleDoubleSlash() == "success"
        helper.queryParam() == "a!b"
    }

    void "test body availability"() {
        given:
        RxHttpClient client = RxHttpClient.create(embeddedServer.getURL())

        when:
        Flowable<HttpResponse> flowable = client.exchange(
                HttpRequest.GET("/get/simple")
        )
        String body
        flowable.firstOrError().subscribe((Consumer){ HttpResponse res ->
            Thread.sleep(3000)
            body = res.getBody(String).orElse(null)
        })
        def conditions = new PollingConditions(timeout: 4)

        then:
        conditions.eventually {
            assert body == 'success'
        }

        cleanup:
        client.stop()
    }

    void "test blocking body availability"() {
        given:
        HttpClient backing = HttpClient.create(embeddedServer.getURL())
        BlockingHttpClient client = backing.toBlocking()

        when:
        HttpResponse res = client.exchange(
                HttpRequest.GET("/get/simple")
        )
        String body = res.getBody(String).orElse(null)

        then:
        body == null

        cleanup:
        backing.stop()
    }

    @Controller("/get")
    static class GetController {

        @Get(uri = "/simple", produces = MediaType.TEXT_PLAIN)
        String simple() {
            return "success"
        }

        @Get("/pojo")
        Book pojo() {
            return new Book(title: "The Stand")
        }

        @Get("/pojoList")
        List<Book> pojoList() {
            return [ new Book(title: "The Stand") ]
        }

        @Get(uri = "/error", produces = MediaType.TEXT_PLAIN)
        HttpResponse error() {
            return HttpResponse.serverError().body("Server error")
        }

        @Get("/jsonError")
        HttpResponse jsonError() {
            return HttpResponse.serverError().body([foo: "bar"])
        }

        @Get("/queryParam")
        String queryParam(@QueryValue String foo) {
            return foo
        }
    }

    static class Book {
        String title
    }

    static class Error {
        String message
    }

    @javax.inject.Singleton
    static class MyGetHelper {
        private final RxStreamingHttpClient rxClientSlash
        private final RxStreamingHttpClient rxClient

        MyGetHelper(@Client("/get/") RxStreamingHttpClient rxClientSlash,
                    @Client("/get") RxStreamingHttpClient rxClient) {
            this.rxClient = rxClient
            this.rxClientSlash = rxClientSlash
        }

        String simple() {
            rxClient.toBlocking().exchange(HttpRequest.GET("simple"), String).body()
        }

        String simplePreceedingSlash() {
            rxClient.toBlocking().exchange(HttpRequest.GET("/simple"), String).body()
        }

        String simpleSlash() {
            rxClientSlash.toBlocking().exchange(HttpRequest.GET("simple"), String).body()
        }

        String simpleDoubleSlash() {
            rxClientSlash.toBlocking().exchange(HttpRequest.GET("/simple"), String).body()
        }

        String queryParam() {
            rxClient.toBlocking().exchange(HttpRequest.GET("/queryParam?foo=a!b"), String).body()
        }
    }
}
