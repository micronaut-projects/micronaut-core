package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class StreamingJsonBodyReleaseSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(['spec.name': 'StreamingJsonBodyReleaseSpec'])
    @Shared
    @AutoCleanup
    EmbeddedServer server = ctx.getBean(EmbeddedServer).start()
    @Shared
    @AutoCleanup
    HttpClient client = ctx.createBean(HttpClient, server.URL)

    def 'a malformed element of a streamed json array is rejected'() {
        when:
        client.toBlocking().exchange(HttpRequest.POST('/streaming-json-release/sum', '[1,"a",3]'), String)

        then:
        // in 4.x, a decoding error of a streamed body element surfaces through the route's
        // reactive result as an unexpected error. The leak detector checks the release
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.INTERNAL_SERVER_ERROR
    }

    def 'the elements a cancelling consumer does not take are released'() {
        expect:
        client.toBlocking().retrieve(HttpRequest.POST('/streaming-json-release/first', '[1,2,{"a":[3,4]},5]'), String) == '1'
    }

    def 'a malformed element of a json-stream list is rejected'() {
        when:
        client.toBlocking().exchange(HttpRequest.POST('/streaming-json-release/list', '1\n"a"\n3')
                .contentType(MediaType.APPLICATION_JSON_STREAM_TYPE), String)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
    }

    def 'a json-stream list is read'() {
        expect:
        client.toBlocking().retrieve(HttpRequest.POST('/streaming-json-release/list', '1\n2\n3')
                .contentType(MediaType.APPLICATION_JSON_STREAM_TYPE), String) == '6'
    }

    @Controller('/streaming-json-release')
    @Requires(property = 'spec.name', value = 'StreamingJsonBodyReleaseSpec')
    static class Ctrl {
        @Post(uri = '/sum', processes = MediaType.APPLICATION_JSON)
        Mono<String> sum(@Body Publisher<Integer> body) {
            return Flux.from(body).reduce(0, Integer::sum).map(String::valueOf)
        }

        @Post(uri = '/first', processes = MediaType.APPLICATION_JSON)
        Mono<String> first(@Body Publisher<Object> body) {
            return Flux.from(body).next().map(String::valueOf)
        }

        @Post(uri = '/list', consumes = MediaType.APPLICATION_JSON_STREAM, produces = MediaType.TEXT_PLAIN)
        String list(@Body List<Integer> body) {
            return String.valueOf(body.sum())
        }
    }
}
