package io.micronaut.http.server.netty.stream

import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import spock.lang.Issue
import spock.lang.Specification

@Issue('https://github.com/micronaut-projects/micronaut-core/issues/4862')
@MicronautTest
class ErrorResponseSpec extends Specification {
    @Inject
    FlowableClient flowableClient

    void 'test flowable error'() {
        when:
        flowableClient.flowableError()

        then:
        def e = thrown(HttpClientResponseException)
        verifyAll {
            e.status == HttpStatus.BAD_REQUEST
            e.message == 'expected flowable error'
        }
    }

    @Client("/flowable")
    static interface FlowableClient {
        @Get(uri = "/error", consumes = MediaType.TEXT_PLAIN)
        Foo flowableError()
    }

    @Controller("/flowable")
    static class FlowableErrorController {

        Logger logger = LoggerFactory.getLogger(FlowableErrorController.class)

        @Get(uri = "/error", produces = MediaType.TEXT_PLAIN)
        Flux<Foo> flowableError() {
            return getError().doOnError(throwable ->
                    logger.error("flowableError endpoint: {}", throwable.getMessage())
            )
        }

        private static Flux<Foo> getError() {
            return Flux.error(
                    new HttpStatusException(HttpStatus.BAD_REQUEST, "expected flowable error")
            )
        }
    }

    static class Foo {}
}
