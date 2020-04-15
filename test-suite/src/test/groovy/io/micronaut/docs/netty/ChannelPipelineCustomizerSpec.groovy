package io.micronaut.docs.netty

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MicronautTest
import io.micronaut.test.annotation.MockBean
import org.zalando.logbook.Logbook
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class ChannelPipelineCustomizerSpec extends Specification {

    @Inject
    Logbook logbook

    @Inject
    @Client("/")
    RxHttpClient client

    void "test logbook is invoked"() {
        given:
        def writeRequest = Stub(Logbook.RequestWritingStage)
        writeRequest.process(_) >> Stub(Logbook.ResponseWritingStage)

        when:
        def result = client.retrieve("/logbook/logged").blockingFirst()

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
