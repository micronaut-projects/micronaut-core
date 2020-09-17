package io.micronaut.http.server.netty.interceptor

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.http.*
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxStreamingHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.sse.Event
import io.micronaut.test.annotation.MicronautTest
import io.reactivex.Flowable
import io.reactivex.Single
import org.reactivestreams.Publisher
import spock.lang.Specification

import javax.inject.Inject
import java.util.concurrent.CompletableFuture

@MicronautTest
@Property(name = 'spec.name', value = 'ResponseChangingFilterSpec')
class ResponseChangingFilterSpec extends Specification {

    @Inject
    @Client("/")
    RxStreamingHttpClient rxClient

    void "test unfiltered routes"() {
        when:
        HttpResponse response = rxClient.exchange("/$action", String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.contentType.orElse(null) == contentType
        response.body.orElse(null) == body

        where:
        action     | body                     | contentType
        "ok"       | "OK"                     | MediaType.TEXT_PLAIN_TYPE
        "reactive" | "Reactive"               | MediaType.APPLICATION_JSON_TYPE
        "simple"   | "Simple"                 | MediaType.TEXT_PLAIN_TYPE
        "flw"      | "[{\"title\":\"Book\"}]" | MediaType.TEXT_JSON_TYPE
        "strm"     | "{\"title\":\"Book\"}"   | MediaType.APPLICATION_JSON_STREAM_TYPE
        "evt"      | "data: EV0\n\n"          | MediaType.TEXT_EVENT_STREAM_TYPE
    }

    void "test header adding filter"() {
        when:
        HttpResponse response = rxClient.exchange("/$action/header", String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.body.orElse(null) == body
        response.header("X-Response-Header") == "true"

        where:
        action     | body
        "ok"       | "OK"
        "reactive" | "Reactive"
        "simple"   | "Simple"
        "flw"      | "[{\"title\":\"Book\"}]"
        "strm"     | "{\"title\":\"Book\"}"
        "evt"      | "data: EV0\n\n"
    }

    void "test response replacing filter: found"() {
        when:
        HttpResponse response = rxClient.exchange("/$action/replace/found", String).blockingFirst()

        then:
        response.status == HttpStatus.FOUND
        response.contentType.orElse(null) == null
        response.body.orElse(null) == null

        where:
        action     | body
        "ok"       | "OK"
        "reactive" | "Reactive"
        "simple"   | "Simple"
        "flw"      | "[{\"title\":\"Book\"}]"
        "strm"     | "{\"title\":\"Book\"}"
        "evt"      | "data: EV0\n\n"
    }

    void "test body replacing filter: text"() {
        when:
        HttpResponse response = rxClient.exchange("/$action/text", String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.contentType.orElse(null) == contentType
        response.body.orElse(null) == "Body"

        where:
        action     | contentType
        "ok"       | MediaType.TEXT_PLAIN_TYPE
        "reactive" | MediaType.APPLICATION_JSON_TYPE
        "simple"   | MediaType.TEXT_PLAIN_TYPE
        "flw"      | MediaType.TEXT_JSON_TYPE
        "strm"     | MediaType.APPLICATION_JSON_STREAM_TYPE
        "evt"      | MediaType.TEXT_EVENT_STREAM_TYPE
    }

    // TODO shouldn't entirely replaced response body reset the content type?
    void "test response replacing filter: text"() {
        when:
        HttpResponse response = rxClient.exchange("/$action/replace/text", String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.contentType.orElse(null) == contentType
        response.body.orElse(null) == "Body"

        where:
        action     | contentType
        "ok"       | MediaType.TEXT_PLAIN_TYPE
        "reactive" | MediaType.APPLICATION_JSON_TYPE
        "simple"   | MediaType.TEXT_PLAIN_TYPE
        "flw"      | MediaType.TEXT_JSON_TYPE
        "strm"     | MediaType.APPLICATION_JSON_STREAM_TYPE
        "evt"      | MediaType.TEXT_EVENT_STREAM_TYPE
    }

    void "test response replacing filter: list+content-type"() {
        when:
        HttpResponse response = rxClient.exchange("/$action/replace/list/content-type", String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.contentType.orElse(null) == MediaType.TEXT_PLAIN_TYPE
        response.body.orElse(null) == "[A, B]"

        where:
        action << ["ok", "reactive", "simple", "flw", "strm", "evt"]
    }

    void "test response replacing filter: single+content-type"() {
        when:
        HttpResponse response = rxClient.exchange("/$action/replace/single/content-type", String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.contentType.orElse(null) == MediaType.TEXT_PLAIN_TYPE
        response.body.orElse(null) == "Body"

        where:
        action << ["ok", "reactive", "simple", "flw", "strm", "evt"]
    }

    void "test response replacing filter: flowable+content-type"() {
        when:
        HttpResponse response = rxClient.exchange("/$action/replace/flowable/content-type", String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.contentType.orElse(null) == MediaType.TEXT_PLAIN_TYPE
        response.body.orElse(null) == "AB"

        where:
        action << ["ok", "reactive", "simple", "flw", "strm", "evt"]
    }

    void "test body replacing filter: books list"() {
        when:
        HttpResponse response = rxClient.exchange("/$action/books/list", String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.contentType.orElse(null) == contentType
        if (contentType.isTextBased()) {
            response.body.orElse(null) == "[Book{A}, Book{B}]"
        } else {
            response.body.orElse(null) == "[{\"title\":\"A\"},{\"title\":\"B\"}]"
        }

        where:
        action     | contentType
        "ok"       | MediaType.TEXT_PLAIN_TYPE
        "reactive" | MediaType.APPLICATION_JSON_TYPE
        "simple"   | MediaType.TEXT_PLAIN_TYPE
        "flw"      | MediaType.TEXT_JSON_TYPE
        "strm"     | MediaType.APPLICATION_JSON_STREAM_TYPE
        "evt"      | MediaType.TEXT_EVENT_STREAM_TYPE
    }

    void "test response replacing filter: books list+content-type"() {
        when:
        HttpResponse response = rxClient.exchange("/$action/replace/books/list/content-type", String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.contentType.orElse(null) == MediaType.APPLICATION_JSON_TYPE
        response.body.orElse(null) == "[{\"title\":\"A\"},{\"title\":\"B\"}]"

        where:
        action << ["ok", "reactive", "simple", "flw", "strm", "evt"]
    }

    void "test response replacing filter: books single+content-type"() {
        when:
        HttpResponse response = rxClient.exchange("/$action/replace/books/single/content-type", String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.contentType.orElse(null) == MediaType.APPLICATION_JSON_TYPE
        response.body.orElse(null) == "{\"title\":\"Body\"}"

        where:
        action << ["ok", "reactive", "simple", "flw", "strm", "evt"]
    }

    void "test response replacing filter: books flowable+content-type"() {
        when:
        HttpResponse response = rxClient.exchange("/$action/replace/books/flowable/content-type", String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.contentType.orElse(null) == MediaType.APPLICATION_JSON_TYPE
        response.body.orElse(null) == "[{\"title\":\"A\"},{\"title\":\"B\"}]"

        where:
        action << ["ok", "reactive", "simple", "flw", "strm", "evt"]
    }

    void "test response replacing filter: books flowable+stream"() {
        when:
        HttpResponse response = rxClient.exchange("/$action/replace/books/flowable/stream", String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.contentType.orElse(null) == MediaType.APPLICATION_JSON_STREAM_TYPE
        response.body.orElse(null) == "{\"title\":\"A\"}{\"title\":\"B\"}"

        where:
        action << ["ok", "reactive", "simple", "flw", "strm", "evt"]
    }

    void "test body replacing filter: events"() {
        when:
        HttpResponse response = rxClient.exchange("/evt/events", String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.contentType.orElse(null) == MediaType.TEXT_EVENT_STREAM_TYPE
        response.body.orElse(null) == "data: EV1\n\ndata: EV2\n\n"
    }

    @Controller
    @Requires(property = 'spec.name', value = "ResponseChangingFilterSpec")
    static class RootController {

        @Get(value = "/ok{/path:.*}", produces = MediaType.TEXT_PLAIN)
        CompletableFuture<String> ok() {
            CompletableFuture.completedFuture("OK")
        }

        @Get("/reactive{/path:.*}")
        Publisher<HttpResponse> reactive() {
            Flowable.just(HttpResponse.ok("Reactive"))
        }

        @Get(value = "/simple{/path:.*}", produces = MediaType.TEXT_PLAIN)
        HttpResponse<Publisher<String>> simple() {
            HttpResponse.ok(Flowable.just("Sim", "ple"));
        }

        @Get(value = "/flw{/path:.*}", produces = MediaType.TEXT_JSON)
        Publisher<Book> flow() {
            Flowable.just(new Book(title: "Book"))
        }

        @Get(value = "/strm{/path:.*}", produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Book> stream() {
            Flowable.just(new Book(title: "Book"))
        }

        @Get(value = "/evt{/path:.*}", produces = MediaType.TEXT_EVENT_STREAM)
        Publisher<Event> events() {
            Flowable.just(Event.of("EV0"))
        }

    }

    @Filter("/**")
    @Requires(property = 'spec.name', value = "ResponseChangingFilterSpec")
    static class ResponseChangingFilter implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            String uri = request.getUri().toString()
            return Publishers.map(chain.proceed(request), response -> {
                if (uri.contains("/replace")) {
                    response = HttpResponse.ok()
                }
                if (uri.contains("/found")) {
                    response.status(HttpStatus.FOUND)
                }
                if (uri.contains("/header")) {
                    response.header("X-Response-Header", "true")
                }
                if (uri.contains("/text")) {
                    response.body("Body")
                }
                if (uri.contains("/books")) {
                    if (uri.contains("/content-type")) {
                        response.contentType(MediaType.APPLICATION_JSON_TYPE);
                    }
                    if (uri.contains("/list")) {
                        response.body([new Book(title: "A"), new Book(title: "B")])
                    }
                    if (uri.contains("/single")) {
                        response.body(Single.just(new Book(title: "Body")))
                    }
                    if (uri.contains("/flowable")) {
                        response.body(Flowable.just(new Book(title: "A"), new Book(title: "B")))
                    }
                } else {
                    if (uri.contains("/content-type")) {
                        response.contentType(MediaType.TEXT_PLAIN);
                    }
                    if (uri.contains("/list")) {
                        response.body(["A", "B"])
                    }
                    if (uri.contains("/single")) {
                        response.body(Single.just("Body"))
                    }
                    if (uri.contains("/flowable")) {
                        response.body(Flowable.just("A", "B"))
                    }
                }
                if (uri.contains("/stream")) {
                    response.contentType(MediaType.APPLICATION_JSON_STREAM)
                }
                if (uri.contains("/events")) {
                    response.contentType(MediaType.TEXT_EVENT_STREAM)
                    response.body(Flowable.just(Event.of("EV1"), Event.of("EV2")))
                }
                return response
            })
        }

    }

    static class Book {
        String title

        @Override
        String toString() {
            return "Book{$title}"
        }
    }

}
