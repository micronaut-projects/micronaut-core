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
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.functions.Consumer
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.annotation.Nullable
import java.time.LocalDate

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
                HttpRequest.GET("/get/error"), Argument.of(String), Argument.of(String)
        ))

        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Server error"
        e.status == HttpStatus.INTERNAL_SERVER_ERROR
        e.response.getBody(String).get() == "Server error"

        cleanup:
        client.stop()
        client.close()
    }

    void "test 500 request with json body"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
        def flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/get/jsonError"), Argument.of(String), Argument.of(Map)
        ))

        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "{foo=bar}"
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

    void "test query parameter with @Client interface"() {
        given:
        MyGetClient client = embeddedServer.applicationContext.getBean(MyGetClient)

        expect:
        client.queryParam('{"service":["test"]}') == '{"service":["test"]}'
        client.queryParam('foo', 'bar') == 'foo-bar'
        client.queryParam('foo%', 'bar') == 'foo%-bar'
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

    void "test that Optional.empty() should return 404"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
        def flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.GET("/get/empty")
        ))

        HttpResponse<Optional<String>> response = flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Page Not Found"
        e.status == HttpStatus.NOT_FOUND

        cleanup:
        client.stop()
        client.close()
    }

    void "test a non empty optional should return the value"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
        String body = client.toBlocking().retrieve(
                HttpRequest.GET("/get/notEmpty"), String
        )


        then:
        body == "not empty"

        cleanup:
        client.stop()
        client.close()
    }

    void 'test format dates with @Format'() {
        given:
        MyGetClient client = embeddedServer.applicationContext.getBean(MyGetClient)
        Date d = new Date(2018, 10, 20)
        LocalDate dt = LocalDate.now()

        expect:
        client.formatDate(d) == d.toString()
        client.formatDateQuery(d) == d.toString()
        client.formatDateTime(dt) == dt.toString()
        client.formatDateTimeQuery(dt) == dt.toString()
    }

    void "test controller slash concatenation"() {
        given:
        BlockingHttpClient client = HttpClient.create(embeddedServer.getURL()).toBlocking()

        expect:
        client.retrieve("/noslash/slash") == "slash"
        client.retrieve("/noslash/slash/") == "slash"
        client.retrieve("/noslash/noslash") == "noslash"
        client.retrieve("/noslash/noslash/") == "noslash"
        client.retrieve("/noslash/startslash") == "startslash"
        client.retrieve("/noslash/startslash/") == "startslash"
        client.retrieve("/noslash/endslash") == "endslash"
        client.retrieve("/noslash/endslash/") == "endslash"

        client.retrieve("/slash/slash") == "slash"
        client.retrieve("/slash/slash/") == "slash"
        client.retrieve("/slash/noslash") == "noslash"
        client.retrieve("/slash/noslash/") == "noslash"
        client.retrieve("/slash/startslash") == "startslash"
        client.retrieve("/slash/startslash/") == "startslash"
        client.retrieve("/slash/endslash") == "endslash"
        client.retrieve("/slash/endslash/") == "endslash"

        client.retrieve("/ending-slash/slash") == "slash"
        client.retrieve("/ending-slash/slash/") == "slash"
        client.retrieve("/ending-slash/noslash") == "noslash"
        client.retrieve("/ending-slash/noslash/") == "noslash"
        client.retrieve("/ending-slash/startslash") == "startslash"
        client.retrieve("/ending-slash/startslash/") == "startslash"
        client.retrieve("/ending-slash/endslash") == "endslash"
        client.retrieve("/ending-slash/endslash/") == "endslash"

        client.retrieve("/noslash") == "noslash"
        client.retrieve("/noslash/") == "noslash"
        client.retrieve("/slash") == "slash"
        client.retrieve("/slash/") == "slash"
        client.retrieve("/startslash") == "startslash"
        client.retrieve("/startslash/") == "startslash"
        client.retrieve("/endslash") == "endslash"
        client.retrieve("/endslash/") == "endslash"

        cleanup:
        client.close()
    }

    void "test a request with a custom host header"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
        String body = client.toBlocking().retrieve(
                HttpRequest.GET("/get/host").header("Host", "http://foo.com"), String
        )


        then:
        body == "http://foo.com"

        cleanup:
        client.close()
    }

    void "test empty list returns ok"() {
        given:
        RxHttpClient client = RxHttpClient.create(embeddedServer.getURL())

        when:
        HttpResponse response = client.exchange(HttpRequest.GET("/get/emptyList"), Argument.listOf(Book)).blockingFirst()

        then:
        noExceptionThrown()
        response.status == HttpStatus.OK
        response.body().isEmpty()

        cleanup:
        client.close()
    }

    void "test single empty list returns ok"() {
        given:
        RxHttpClient client = RxHttpClient.create(embeddedServer.getURL())

        when:
        HttpResponse response = client.exchange(HttpRequest.GET("/get/emptyList/single"), Argument.listOf(Book)).blockingFirst()

        then:
        noExceptionThrown()
        response.status == HttpStatus.OK
        response.body().isEmpty()

        cleanup:
        client.close()
    }

    void "test mono empty list returns ok"() {
        given:
        RxHttpClient client = RxHttpClient.create(embeddedServer.getURL())

        when:
        HttpResponse response = client.exchange(HttpRequest.GET("/get/emptyList/mono"), Argument.listOf(Book)).blockingFirst()

        then:
        noExceptionThrown()
        response.status == HttpStatus.OK
        response.body().isEmpty()

        cleanup:
        client.close()
    }

    void "test completable returns 200"() {
        when:
        MyGetClient client = embeddedServer.applicationContext.getBean(MyGetClient)
        def ex = client.completableError().blockingGet()

        then:
        client.completable().blockingGet() == null
        ex instanceof HttpClientResponseException
        ex.message.contains("completable error")
    }

    void "test setting query params on the request"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
        MutableHttpRequest request = HttpRequest.GET("/get/multipleQueryParam?foo=x")
        request.parameters.add('bar', 'y')
        String body = client.toBlocking().retrieve(request)

        then:
        noExceptionThrown()
        body == 'x-y'

        cleanup:
        client.close()
    }

    void "test overriding the URL"() {
        def client = embeddedServer.applicationContext.getBean(OverrideUrlClient)

        when:
        String val = client.overrideUrl(embeddedServer.getURL().toString())

        then:
        val == "success"
    }

    void "test multiple uris"() {
        def client = embeddedServer.applicationContext.getBean(MyGetClient)

        when:
        String val = client.multiple()

        then:
        val == "multiple mappings"

        when:
        val = client.multipleMappings()

        then:
        val == "multiple mappings"
    }

    void "test exploded query param request URI"() {
        when:
        MyGetClient client = embeddedServer.applicationContext.getBean(MyGetClient)
        String requestUri = client.queryParamExploded(["abc", "xyz"])

        then:
        requestUri.endsWith("bar=abc&bar=xyz")
    }

    void "test multiple exploded query param request URI"() {
        when:
        MyGetClient client = embeddedServer.applicationContext.getBean(MyGetClient)
        String requestUri = client.multipleExplodedQueryParams(["abc", "xyz"], "random")

        then:
        requestUri.endsWith("bar=abc&bar=xyz&tag=random")
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/2782")
    void "test single letter uri"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
        MutableHttpRequest request = HttpRequest.GET("/get/a")
        String body = client.toBlocking().retrieve(request)

        then:
        noExceptionThrown()
        body == 'success'

        cleanup:
        client.close()
    }

    @Controller("/get")
    static class GetController {

        @Get(value = "a", produces = MediaType.TEXT_PLAIN)
        String a() {
            return "success"
        }

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

        @Get("/emptyList")
        List<Book> emptyList() {
            return []
        }

        @Get("/emptyList/single")
        Single<List<Book>> emptyListSingle() {
            return Single.just([])
        }

        @Get("/emptyList/mono")
        Mono<List<Book>> emptyListMono() {
            return Mono.just([])
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

        @Get("/queryParamExploded{?bar*}")
        String queryParamExploded(@QueryValue("bar") List<String> foo, HttpRequest<?> request) {
            return request.getUri().toString()
        }

        @Get("/multipleExplodedQueryParams{?bar*,tag}")
        String multipleExplodedQueryParams(@QueryValue("bar") List<String> foo, @Nullable @QueryValue("tag") String label, HttpRequest<?> request) {
            return request.getUri().toString()
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

        @Get("/completable")
        Completable completable(){
            return Completable.complete()
        }

        @Get("/completable/error")
        Completable completableError() {
            return Completable.error(new RuntimeException("completable error"))
        }

        @Get(uris = ["/multiple", "/multiple/mappings"])
        String multipleMappings() {
            return "multiple mappings"
        }
    }


    @Controller("noslash")
    static class NoSlashController {

        @Get("/slash/")
        String slash() {
            "slash"
        }

        @Get("noslash")
        String noSlash() {
            "noslash"
        }

        @Get("/startslash")
        String startSlash() {
            "startslash"
        }

        @Get("endslash/")
        String endSlash() {
            "endslash"
        }
    }


    @Controller("/slash")
    static class SlashController {

        @Get("/slash/")
        String slash() {
            "slash"
        }

        @Get("noslash")
        String noSlash() {
            "noslash"
        }

        @Get("/startslash")
        String startSlash() {
            "startslash"
        }

        @Get("endslash/")
        String endSlash() {
            "endslash"
        }
    }

    @Controller("/ending-slash/")
    static class EndingSlashController {

        @Get("/slash/")
        String slash() {
            "slash"
        }

        @Get("noslash")
        String noSlash() {
            "noslash"
        }

        @Get("/startslash")
        String startSlash() {
            "startslash"
        }

        @Get("endslash/")
        String endSlash() {
            "endslash"
        }
    }

    @Controller
    static class SlashRootController {

        @Get("/slash/")
        String slash() {
            "slash"
        }

        @Get("noslash")
        String noSlash() {
            "noslash"
        }

        @Get("/startslash")
        String startSlash() {
            "startslash"
        }

        @Get("endslash/")
        String endSlash() {
            "endslash"
        }
    }

    static class Book {
        String title
    }

    static class Error {
        String message
    }

    @Client("/get")
    static interface MyGetClient {
        @Get(value = "/simple", produces = MediaType.TEXT_PLAIN)
        String simple()

        @Get("/pojo")
        Book pojo()

        @Get("/pojoList")
        List<Book> pojoList()

        @Get(value = "/error", produces = MediaType.TEXT_PLAIN)
        HttpResponse error()

        @Get("/jsonError")
        HttpResponse jsonError()

        @Get("/queryParam")
        String queryParam(@QueryValue String foo)

        @Get("/queryParamExploded{?bar*}")
        String queryParamExploded(@QueryValue("bar") List<String> foo)

        @Get("/multipleExplodedQueryParams{?bar*,tag}")
        String multipleExplodedQueryParams(@QueryValue("bar") List<String> foo, @QueryValue("tag") String label)

        @Get("/multipleQueryParam")
        String queryParam(@QueryValue String foo, @QueryValue String bar)

        @Get("/date/{myDate}")
        String formatDate(@Format('yyyy-MM-dd') Date myDate)

        @Get("/dateTime/{myDate}")
        String formatDateTime(@Format('yyyy-MM-dd') LocalDate myDate)

        @Get("/dateQuery")
        String formatDateQuery(@QueryValue @Format('yyyy-MM-dd') Date myDate)

        @Get("/dateTimeQuery")
        String formatDateTimeQuery(@QueryValue @Format('yyyy-MM-dd') LocalDate myDate)

        @Get("/completable")
        Completable completable()

        @Get("/completable/error")
        Completable completableError()

        @Get("/multiple")
        String multiple()

        @Get("/multiple/mappings")
        String multipleMappings()
    }

    @Client("http://not.used")
    static interface OverrideUrlClient {

        @Get(value = "{+url}/get/simple", consumes = MediaType.TEXT_PLAIN)
        String overrideUrl(String url);

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
