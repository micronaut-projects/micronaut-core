/*
 * Copyright 2018 original authors
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
package org.particleframework.http.client

import io.reactivex.Flowable
import org.particleframework.context.ApplicationContext
import org.particleframework.core.type.Argument
import org.particleframework.http.HttpRequest
import org.particleframework.http.HttpResponse
import org.particleframework.http.HttpStatus
import org.particleframework.http.MediaType
import org.particleframework.http.annotation.Controller
import org.particleframework.http.client.exceptions.HttpClientResponseException
import org.particleframework.runtime.server.EmbeddedServer
import org.particleframework.web.router.annotation.Get
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

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
                HttpRequest.GET("/get/simple")
        ))
        Optional<String> body = flowable.map({res ->
            res.getBody(String)}
        ).blockingFirst()

        then:
        body.isPresent()
        body.get() == 'success'

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
        e.message == "Not Found"
        e.status == HttpStatus.NOT_FOUND
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
    }

    void "test simple blocking get request"() {
        given:
        BlockingHttpClient client = HttpClient.create(embeddedServer.getURL()).toBlocking()

        when:
        HttpResponse<String> response = client.exchange(
                HttpRequest.GET("/get/simple"),
                String
        )

        def body = response.getBody()

        then:
        body.isPresent()
        body.get() == 'success'

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
    }

    void "test simple exchange request with POJO"() {
        given:
        def context = ApplicationContext.run()
        HttpClient client = context.createBean(HttpClient, [url:embeddedServer.getURL()])

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

    }

    void "test simple retrieve request with POJO"() {
        given:
        def context = ApplicationContext.run()
        HttpClient client = context.createBean(HttpClient, [url:embeddedServer.getURL()])

        when:
        Flowable<Book> flowable = Flowable.fromPublisher(client.retrieve(
                HttpRequest.GET("/get/pojo"), Book
        ))

        Book book = flowable.blockingFirst()

        then:
        book != null
        book.title == "The Stand"
    }

    void "test simple get request with POJO list"() {
        given:
        def context = ApplicationContext.run()
        HttpClient client = context.createBean(HttpClient, [url:embeddedServer.getURL()])

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
    }

    static class Book {
        String title
    }
}
