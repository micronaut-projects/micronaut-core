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
package org.particleframework.http.server.netty.stream

import io.reactivex.Flowable
import org.particleframework.context.ApplicationContext
import org.particleframework.http.HttpRequest
import org.particleframework.http.HttpResponse
import org.particleframework.http.MediaType
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.http.client.RxStreamingHttpClient
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class JsonStreamSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    void "test json stream response content type"() {
        given:
        RxStreamingHttpClient streamingHttpClient = embeddedServer.applicationContext.createBean(RxStreamingHttpClient, embeddedServer.getURL())

        HttpResponse response = streamingHttpClient.exchangeStream(HttpRequest.GET('/json/stream')).blockingFirst()

        expect:
        response.contentType.isPresent()
        response.contentType.get() == MediaType.APPLICATION_JSON_STREAM_TYPE

    }

    @Controller("/json/stream")
    static class StreamController {


        @Get(uri = '/', produces = MediaType.APPLICATION_JSON_STREAM)
        Flowable<Book> stream() {
            return Flowable.just(new Book(title: "The Stand"), new Book(title: "The Shining"))
        }
    }

    static class Book {
        String title
    }
}
