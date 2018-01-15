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
package org.particleframework.http.client.rxjava2

import io.reactivex.Flowable
import org.particleframework.context.ApplicationContext
import org.particleframework.context.annotation.Argument
import org.particleframework.http.HttpRequest
import org.particleframework.http.HttpResponse
import org.particleframework.http.HttpStatus
import org.particleframework.http.MediaType
import org.particleframework.http.annotation.Body
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Header
import org.particleframework.http.client.BlockingHttpClient
import org.particleframework.http.client.HttpClient
import org.particleframework.runtime.server.EmbeddedServer
import org.particleframework.web.router.annotation.Head
import org.particleframework.web.router.annotation.Post
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class RxHttpPostSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
    @Shared @AutoCleanup RxHttpClient client = context.createBean(RxHttpClient, [url:embeddedServer.getURL()])

    void "test simple post exchange request with JSON"() {
        when:
        Flowable<HttpResponse<Book>> flowable = client.exchange(
                HttpRequest.POST("/post/simple", new Book(title: "The Stand"))
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                Book
        )
        HttpResponse<Book> response = flowable.blockingFirst()
        Optional<Book> body = response.getBody()

        then:
        response.status == HttpStatus.OK
        response.contentType.get() == MediaType.APPLICATION_JSON_TYPE
        response.contentLength == 21
        body.isPresent()
        body.get() instanceof Book
        body.get().title == 'The Stand'
    }

    void "test simple post retrieve request with JSON"() {
        when:
        Flowable<Book> flowable = client.retrieve(
                HttpRequest.POST("/post/simple", new Book(title: "The Stand"))
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                Book
        )
        Book book = flowable.blockingFirst()

        then:
        book.title == "The Stand"
    }

    void "test simple post retrieve blocking request with JSON"() {
        when:
        BlockingHttpClient blockingHttpClient = client.toBlocking()
        Book book = blockingHttpClient.retrieve(
                HttpRequest.POST("/post/simple", new Book(title: "The Stand"))
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                Book
        )

        then:
        book.title == "The Stand"
    }

    @Controller('/post')
    static class PostController {

        @Post('/simple')
        Book simple(@Body Book book, @Header String contentType, @Header long contentLength, @Header accept, @Header('X-My-Header') custom) {
            assert contentType == MediaType.APPLICATION_JSON
            assert contentLength == 21
            assert accept == MediaType.APPLICATION_JSON
            assert custom == 'Foo'
            return book
        }

    }
    static class Book {
        String title
    }
}
