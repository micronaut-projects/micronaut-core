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
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import io.reactivex.Single
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author graemerocher
 * @since 1.0
 */
class ServerRedirectSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer)


    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/217")
    void "test https redirect"() {

        given:"An HTTPS URL issues an HTTPS"
        YoutubeClient youtubeClient=  embeddedServer.getApplicationContext().getBean(YoutubeClient)
        def client = HttpClient.create(new URL("https://www.youtube.com"))
        def response= client
                .toBlocking().retrieve("/")

        expect:"The response was returned and doesn't loop"
        response
        youtubeClient.test().blockingGet()

        cleanup:
        client.close()
    }

    @Unroll
    void "test http client follows #type redirects for regular exchange requests"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        expect:
        client.toBlocking().retrieve("/redirect/$type") == result

        cleanup:
        client.stop()
        client.close()

        where:
        type        | result
        'permanent' | 'good'
        'temporary' | 'good'
        'moved'     | 'good'
        'seeOther'  | 'good'

    }

    @Unroll
    void "test http client follows #type redirects for regular stream requests"() {
        given:
        RxStreamingHttpClient client = RxStreamingHttpClient.create(embeddedServer.getURL())

        expect:
        client.jsonStream(HttpRequest.GET("/redirect/stream/$type"), Book).blockingFirst().title == "The Stand"

        cleanup:
        client.stop()
        client.close()

        where:
        type        | result
        'permanent' | 'good'
        'temporary' | 'good'
        'moved'     | 'good'
        'seeOther'  | 'good'

    }

    void "test stream redirect headers"() {
        given:
        RxStreamingHttpClient client = RxStreamingHttpClient.create(embeddedServer.getURL())

        when:
        String response = ((RxStreamingHttpClient) client).exchangeStream(
                HttpRequest.GET("/redirect/stream/title").accept(MediaType.TEXT_EVENT_STREAM_TYPE))
                .map({res ->
                    new String(res.body().toByteArray())
                })
                .blockingFirst()

        then:
        response == "data: The Stand\n\n"
    }

    void "test redirect headers"() {
        given:
        HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/redirect/title").accept(MediaType.TEXT_PLAIN_TYPE, MediaType.APPLICATION_JSON_TYPE), String)

        then:
        response.status() == HttpStatus.OK
        response.body() == "The Stand"

    }

    @Controller("/redirect")
    static class RedirectController {

        @Get("/permanent")
        HttpResponse permanent() {
            HttpResponse.permanentRedirect(URI.create('/redirect'))
        }

        @Get("/temporary")
        HttpResponse temporary() {
            HttpResponse.temporaryRedirect(URI.create('/redirect'))
        }

        @Get("/moved")
        HttpResponse moved() {
            HttpResponse.redirect(URI.create('/redirect'))
        }

        @Get("/seeOther")
        HttpResponse seeOther() {
            HttpResponse.seeOther(URI.create('/redirect'))
        }

        @Get("/title")
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse textTitle() {
            HttpResponse.seeOther(URI.create("/redirect/text"))
        }

        @Get
        String home() {
            return "good"
        }

        @Get("/text")
        @Produces([MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON])
        HttpResponse<?> title(final HttpHeaders headers) {
            if (headers.accept().contains(MediaType.TEXT_PLAIN_TYPE)) {
                return HttpResponse.ok("The Stand").contentType(MediaType.TEXT_PLAIN)
            }
            return HttpResponse.ok(new Book(title: "The Stand")).contentType(MediaType.APPLICATION_JSON)
        }
    }

    @Controller("/redirect/stream")
    static class StreamRedirectController {

        @Get("/permanent")
        HttpResponse permanent() {
            HttpResponse.permanentRedirect(URI.create('/redirect/stream'))
        }

        @Get("/temporary")
        HttpResponse temporary() {
            HttpResponse.temporaryRedirect(URI.create('/redirect/stream'))
        }

        @Get("/moved")
        HttpResponse moved() {
            HttpResponse.redirect(URI.create('/redirect/stream'))
        }

        @Get("/seeOther")
        HttpResponse seeOther() {
            HttpResponse.seeOther(URI.create('/redirect/stream'))
        }

        @Get("/title")
        @Produces(MediaType.TEXT_EVENT_STREAM)
        HttpResponse textTitle() {
            HttpResponse.seeOther(URI.create("/redirect/stream/text"))
        }

        @Get
        @Produces(MediaType.APPLICATION_JSON_STREAM)
        Flowable<Book> home() {
            Flowable.just(new Book(title: "The Stand"))
        }

        @Get("/text")
        @Produces([MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON_STREAM])
        Flowable<HttpResponse<?>> title(final HttpHeaders headers) {
            if (headers.accept().contains(MediaType.TEXT_EVENT_STREAM_TYPE)) {
                return Flowable.just(HttpResponse.ok("The Stand").contentType(MediaType.TEXT_EVENT_STREAM))
            }
            return Flowable.just(HttpResponse.ok(new Book(title: "The Stand")).contentType(MediaType.APPLICATION_JSON_STREAM))

        }

    }

    @Client("https://youtube.com")
    static interface YoutubeClient {
        @Get
        Single<String> test()
    }


    static class Book {
        String title
    }
}
