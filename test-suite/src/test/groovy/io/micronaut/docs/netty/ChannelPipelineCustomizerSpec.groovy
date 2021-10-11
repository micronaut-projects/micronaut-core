package io.micronaut.docs.netty

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import org.zalando.logbook.Logbook
import reactor.core.publisher.Flux
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest
class ChannelPipelineCustomizerSpec extends Specification {

    @Inject
    Logbook logbook

    @Inject
    @Client("/")
    HttpClient client

    void "test logbook is invoked"() {
        given:
        def writeRequest = Stub(Logbook.RequestWritingStage)
        writeRequest.process(_) >> Stub(Logbook.ResponseWritingStage)

        when:
        def result = client.toBlocking().retrieve("/logbook/logged")

        then:"2 logs, one for the client and one for the server"
        2 * logbook.process(_) >> writeRequest
        result == 'hello'
    }

    @MockBean(Logbook.class)
    Logbook logbook() {
        Mock(Logbook)
    }

    @Controller("/logbook/logged")
    static class LoggedController {
        @Get("/")
        @Produces(MediaType.TEXT_PLAIN)
        String index() {
            return "hello"
        }
    }
}
