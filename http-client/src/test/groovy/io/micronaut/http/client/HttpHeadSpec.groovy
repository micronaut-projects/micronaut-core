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
package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.format.Format
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Head
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.annotation.MicronautTest
import io.reactivex.Flowable
import io.reactivex.functions.Consumer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.inject.Inject
import java.time.LocalDate

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@MicronautTest
class HttpHeadSpec extends Specification {

    @Inject
    @Client("/")
    RxHttpClient client

    @Inject
    MyGetClient myGetClient

    @Inject
    MyGetHelper myGetHelper

    void "test simple head request"() {
        when:
        def flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.HEAD("/head/simple").header("Accept-Encoding", "gzip")
        ))
        Optional<String> body = flowable.map({res ->
            res.getBody(String)}
        ).blockingFirst()

        then:
        !body.isPresent()

    }


    void "test simple 404 request"() {
        when:
        def flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.HEAD("/head/doesntexist")
        ))

        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Not Found"
        e.status == HttpStatus.NOT_FOUND
    }

    void "test 500 request with body"() {
        when:
        def flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.HEAD("/head/error"), Argument.of(String), Argument.of(String)
        ))

        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error"
        e.status == HttpStatus.INTERNAL_SERVER_ERROR
        !e.response.getBody(String).isPresent()
    }

    void "test 500 request with json body"() {
        when:
        def flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.HEAD("/get/jsonError"), Argument.of(String), Argument.of(Map)
        ))

        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error"
        e.status == HttpStatus.INTERNAL_SERVER_ERROR
    }

    void "test simple 404 request as VndError"() {
        when:
        def flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/head/doesntexist")
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
    }

    void "test simple blocking get request"() {

        given:
        BlockingHttpClient client = client.toBlocking()

        when:
        HttpResponse<String> response = client.exchange(
                HttpRequest.HEAD("/head/simple"),
                String
        )

        def body = response.getBody()

        then:
        !body.isPresent()

    }

    void "test simple get request with type"() {
        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.HEAD("/head/simple"), String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody()

        then:
        response.status == HttpStatus.OK
        !body.isPresent()

    }

    void "test simple exchange request with POJO"() {

        when:
        Flowable<HttpResponse<Book>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.HEAD("/head/pojo"), Book
        ))

        HttpResponse<Book> response = flowable.blockingFirst()
        Optional<Book> body = response.getBody()

        then:
        !response.contentType.isPresent()
        response.contentLength == -1
        response.status == HttpStatus.OK
        !body.isPresent()
    }

    void "test simple retrieve request with POJO"() {
        when:
        Flowable<Book> flowable = Flowable.fromPublisher(client.retrieve(
                HttpRequest.HEAD("/head/pojo"), Book
        )).blockingFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.message == "Empty body"
    }

    void "test simple get request with POJO list"() {
        when:
        Flowable<HttpResponse<List<Book>>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.HEAD("/head/pojoList"), Argument.of(List, Book)
        ))

        HttpResponse<List<Book>> response = flowable.blockingFirst()
        Optional<List<Book>> body = response.getBody()

        then:
        !response.contentType.isPresent()
        response.contentLength == -1
        response.status == HttpStatus.OK
        !body.isPresent()
    }

    void "test get with @Client"() {
        given:
        MyGetHelper helper = this.myGetHelper

        expect:
        helper.simple() == null
        helper.simpleSlash() == null
        helper.simplePreceedingSlash() == null
        helper.simpleDoubleSlash() == null
        helper.queryParam() == null
    }

    void "test query parameter with @Client interface"() {
        given:
        MyGetClient client = this.myGetClient

        when:
        client.queryParam('{"service":["test"]}')

        then:
        def ex = thrown(HttpClientResponseException)
        ex.message == "Empty body"

        when:
        client.queryParam('foo', 'bar')

        then:
        ex = thrown(HttpClientResponseException)
        ex.message == "Empty body"

        when:
        client.queryParam('foo%', 'bar')

        then:
        ex = thrown(HttpClientResponseException)
        ex.message == "Empty body"
    }

    void "test body availability"() {
        when:
        Flowable<HttpResponse> flowable = client.exchange(
                HttpRequest.HEAD("/head/simple")
        )
        String body
        flowable.firstOrError().subscribe((Consumer){ HttpResponse res ->
            Thread.sleep(3000)
            body = res.getBody(String).orElse(null)
        })
        def conditions = new PollingConditions(timeout: 4)

        then:
        conditions.eventually {
            body == null
        }
    }

    void "test that Optional.empty() should return 404"() {
        when:
        def flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.HEAD("/head/empty")
        ))

        HttpResponse<Optional<String>> response = flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Not Found"
        e.status == HttpStatus.NOT_FOUND
    }

    void "test a non empty optional should return the value"() {
        when:
        String body = client.toBlocking().retrieve(
                HttpRequest.HEAD("/head/notEmpty"), String
        )

        then:
        def ex = thrown(HttpClientResponseException)
        ex.message == "Empty body"
    }

    void 'test format dates with @Format'() {
        given:
        MyGetClient client = this.myGetClient
        Date d = new Date(2018, 10, 20)
        LocalDate dt = LocalDate.now()

        when:
        client.formatDate(d)

        then:
        def ex = thrown(HttpClientResponseException)
        ex.message == "Empty body"

        when:
        client.formatDateQuery(d)

        then:
        ex = thrown(HttpClientResponseException)
        ex.message == "Empty body"

        when:
        client.formatDateTime(dt)

        then:
        ex = thrown(HttpClientResponseException)
        ex.message == "Empty body"

        when:
        client.formatDateTimeQuery(dt)

        then:
        ex = thrown(HttpClientResponseException)
        ex.message == "Empty body"
    }

    void "test a request with a custom host header"() {
        when:
        String body = client.toBlocking().retrieve(
                HttpRequest.HEAD("/head/host").header("Host", "http://foo.com"), String
        )

        then:
        def ex = thrown(HttpClientResponseException)
        ex.message == "Empty body"

    }

    void "test a disabled head route"() {
        given:
        MyGetClient myGetClient = this.myGetClient

        when:
        myGetClient.noHead()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.message == "Method Not Allowed"

        when:
        String body = client.toBlocking().retrieve(HttpRequest.GET("/head/no-head"), String)

        then:
        body == "success"

        cleanup:
        client.close()
    }

    void "test multiple uris"() {
        def client = this.myGetClient

        when:
        String val = client.multiple().header("X-Test")

        then:
        val == "multiple mappings"

        when:
        val = client.multipleMappings().header("X-Test")

        then:
        val == "multiple mappings"
    }

    @Controller("/head")
    static class GetController {

        @Get(value = "/simple", produces = MediaType.TEXT_PLAIN)
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

        @Get(value = "/error", produces = MediaType.TEXT_PLAIN)
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

        @Get("/multipleQueryParam")
        String queryParam(@QueryValue String foo, @QueryValue String bar) {
            return foo + '-' + bar
        }

        @Get("/empty")
        Optional<String> empty() {
            return Optional.empty()
        }

        @Get("/notEmpty")
        Optional<String> notEmpty() {
            return Optional.of("not empty")
        }

        @Get("/date/{myDate}")
        String formatDate(@Format('yyyy-MM-dd') Date myDate) {
            return myDate.toString()
        }

        @Get("/dateTime/{myDate}")
        String formatDateTime(@Format('yyyy-MM-dd') LocalDate myDate) {
            return myDate.toString()
        }

        @Get("/dateQuery")
        String formatDateQuery(@QueryValue @Format('yyyy-MM-dd') Date myDate) {
            return myDate.toString()
        }

        @Get("/dateTimeQuery")
        String formatDateTimeQuery(@QueryValue @Format('yyyy-MM-dd') LocalDate myDate) {
            return myDate.toString()
        }

        @Get("/host")
        String hostHeader(@Header String host) {
            return host
        }

        @Get(uri = "/no-head", produces = MediaType.TEXT_PLAIN, headRoute = false)
        String noHead() {
            return "success"
        }

        @Head(uris = ["/multiple", "/multiple/mappings"])
        HttpResponse multipleMappings() {
            return HttpResponse.ok().header("X-Test", "multiple mappings")
        }
    }

    static class Book {
        String title
    }

    static class Error {
        String message
    }

    @Client("/head")
    static interface MyGetClient {
        @Head(value = "/simple")
        String simple()

        @Head("/pojo")
        Book pojo()

        @Head("/pojoList")
        List<Book> pojoList()

        @Head(value = "/error")
        HttpResponse error()

        @Head("/jsonError")
        HttpResponse jsonError()

        @Head("/queryParam")
        String queryParam(@QueryValue String foo)

        @Head("/multipleQueryParam")
        String queryParam(@QueryValue String foo, @QueryValue String bar)

        @Head("/date/{myDate}")
        String formatDate(@Format('yyyy-MM-dd') Date myDate)

        @Head("/dateTime/{myDate}")
        String formatDateTime(@Format('yyyy-MM-dd') LocalDate myDate)

        @Head("/dateQuery")
        String formatDateQuery(@QueryValue @Format('yyyy-MM-dd') Date myDate)

        @Head("/dateTimeQuery")
        String formatDateTimeQuery(@QueryValue @Format('yyyy-MM-dd') LocalDate myDate)

        @Head(value = "/no-head")
        String noHead()

        @Head("/multiple")
        HttpResponse multiple()

        @Head("/multiple/mappings")
        HttpResponse multipleMappings()
    }

    @javax.inject.Singleton
    static class MyGetHelper {
        private final RxStreamingHttpClient rxClientSlash
        private final RxStreamingHttpClient rxClient

        MyGetHelper(@Client("/head/") RxStreamingHttpClient rxClientSlash,
                    @Client("/head") RxStreamingHttpClient rxClient) {
            this.rxClient = rxClient
            this.rxClientSlash = rxClientSlash
        }

        String simple() {
            rxClient.toBlocking().exchange(HttpRequest.HEAD("simple"), String).body()
        }

        String simplePreceedingSlash() {
            rxClient.toBlocking().exchange(HttpRequest.HEAD("/simple"), String).body()
        }

        String simpleSlash() {
            rxClientSlash.toBlocking().exchange(HttpRequest.HEAD("simple"), String).body()
        }

        String simpleDoubleSlash() {
            rxClientSlash.toBlocking().exchange(HttpRequest.HEAD("/simple"), String).body()
        }

        String queryParam() {
            rxClient.toBlocking().exchange(HttpRequest.HEAD("/queryParam?foo=a!b"), String).body()
        }
    }
}
