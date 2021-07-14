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

import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.*
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Shared
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Unroll

/**
 * @author graemerocher
 * @since 1.0
 */
class ServerRedirectSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer, [
                    'spec.name': 'ServerRedirectSpec',
            ])

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/217")
    void "test https redirect"() {

        given:"An HTTPS URL issues an HTTPS"
        YoutubeClient youtubeClient=  embeddedServer.getApplicationContext().getBean(YoutubeClient)
        HttpClient client = HttpClient.create(new URL("https://www.youtube.com"))
        String declarativeResult = Mono.from(youtubeClient.test()).block()
        String response= client
                .toBlocking().retrieve("/")
//
        expect:"The response was returned and doesn't loop"
        response
        declarativeResult

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
        StreamingHttpClient client = StreamingHttpClient.create(embeddedServer.getURL())

        expect:
        Flux.from(client.jsonStream(HttpRequest.GET("/redirect/stream/$type"), Book)).blockFirst().title == "The Stand"

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
        StreamingHttpClient client = StreamingHttpClient.create(embeddedServer.getURL())

        when:
        String response = Flux.from(client.exchangeStream(
                HttpRequest.GET("/redirect/stream/title").accept(MediaType.TEXT_EVENT_STREAM_TYPE)))
                .map({res ->
                    new String(res.body().toByteArray())
                })
                .blockFirst()

        then:
        response == "data: The Stand"
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

    void "test redirect with no base URL client"() {
        given:
        HttpClient client = HttpClient.create(null)
        UriBuilder uriBuilder = UriBuilder.of(embeddedServer.getScheme() + "://" + embeddedServer.getHost())
                .port(embeddedServer.getPort())
                .path("/redirect/temporary")

        expect:
        client.toBlocking().retrieve(HttpRequest.GET(uriBuilder.build())) == 'good'

        cleanup:
        client.close()
    }

    @Requires(property = 'spec.name', value = 'ServerRedirectSpec')
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

    @Requires(property = 'spec.name', value = 'ServerRedirectSpec')
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
        Publisher<Book> home() {
            Flux.just(new Book(title: "The Stand"))
        }

        @Get("/text")
        @Produces([MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON_STREAM])
        HttpResponse<Flux<?>> title(final HttpHeaders headers) {
            if (headers.accept().contains(MediaType.TEXT_EVENT_STREAM_TYPE)) {
                return HttpResponse.ok(Flux.just("The Stand")).contentType(MediaType.TEXT_EVENT_STREAM)
            }
            return HttpResponse.ok(Flux.just(new Book(title: "The Stand"))).contentType(MediaType.APPLICATION_JSON_STREAM)
        }
    }

    @Requires(property = 'spec.name', value = 'ServerRedirectSpec')
    @Client("https://www.youtube.com")
    static interface YoutubeClient {
        @Get
        @SingleResult
        Publisher<String> test()
    }

    static class Book {
        String title
    }
}
