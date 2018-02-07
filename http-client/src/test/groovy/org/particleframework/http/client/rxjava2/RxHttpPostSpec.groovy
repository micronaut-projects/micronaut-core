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
import org.particleframework.http.HttpRequest
import org.particleframework.http.HttpResponse
import org.particleframework.http.HttpStatus
import org.particleframework.http.MediaType
import org.particleframework.http.client.BlockingHttpClient
import org.particleframework.http.client.HttpPostSpec
import org.particleframework.http.client.RxHttpClient
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class RxHttpPostSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
    @Shared @AutoCleanup RxHttpClient client = context.createBean(RxHttpClient, embeddedServer.getURL())

    void "test simple post exchange request with JSON"() {
        when:
        Flowable<HttpResponse<HttpPostSpec.Book>> flowable = client.exchange(
                HttpRequest.POST("/post/simple", new HttpPostSpec.Book(title: "The Stand", pages: 1000))
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                HttpPostSpec.Book
        )
        HttpResponse<HttpPostSpec.Book> response = flowable.blockingFirst()
        Optional<HttpPostSpec.Book> body = response.getBody()

        then:
        response.status == HttpStatus.OK
        response.contentType.get() == MediaType.APPLICATION_JSON_TYPE
        response.contentLength == 34
        body.isPresent()
        body.get() instanceof HttpPostSpec.Book
        body.get().title == 'The Stand'
    }

    void "test simple post retrieve request with JSON"() {
        when:
        Flowable<HttpPostSpec.Book> flowable = client.retrieve(
                HttpRequest.POST("/post/simple", new HttpPostSpec.Book(title: "The Stand", pages: 1000))
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                HttpPostSpec.Book
        )
        HttpPostSpec.Book book = flowable.blockingFirst()

        then:
        book.title == "The Stand"
    }

    void "test simple post retrieve blocking request with JSON"() {
        when:
        BlockingHttpClient blockingHttpClient = client.toBlocking()
        HttpPostSpec.Book book = blockingHttpClient.retrieve(
                HttpRequest.POST("/post/simple", new HttpPostSpec.Book(title: "The Stand", pages: 1000))
                        .accept(MediaType.APPLICATION_JSON_TYPE)
                        .header("X-My-Header", "Foo"),

                HttpPostSpec.Book
        )

        then:
        book.title == "The Stand"
    }


}
