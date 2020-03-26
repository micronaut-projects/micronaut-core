package io.micronaut.http.client

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.test.annotation.MicronautTest
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class ResponseAndStreamSpec extends Specification {

    @Inject
    ResponseStreamClient client

    void "test switch to streaming on flowable body"() {
        when:
        def response = client.go()

        then:
        response.body() == 'chunk1chunk2chunk3'
    }

    @Client('/test/response-stream')
    static interface ResponseStreamClient {
        @Get('/model')
        HttpResponse<String> go()
    }

    @Controller('/test/response-stream')
    static class ResponseStreamController {

        @Get('/model')
        Map<String, String> go() {
            return [test:'model']
        }
    }

    @Filter('/test/response-stream/*')
    static class MyFilter implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(
                HttpRequest<?> request, ServerFilterChain chain) {
            return Flowable.fromPublisher(chain.proceed()).map { MutableHttpResponse<?> response ->
                return response.body(Flowable.fromIterable([
                        "chunk1",
                        "chunk2",
                        "chunk3",
                ])).contentType(MediaType.TEXT_PLAIN)
            }
        }
    }
}
