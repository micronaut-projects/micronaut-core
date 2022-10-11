package io.micronaut.docs.sse

import io.micronaut.docs.streaming.Headline
import io.micronaut.http.sse.Event
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import reactor.core.publisher.Flux
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest
class HeadlineControllerSpec extends Specification {

    // tag::streamingClient[]
    @Inject HeadlineClient headlineClient

    void testClientAnnotationStreaming() throws Exception {
        when:
            Event<Headline> headline = Flux.from(headlineClient.streamHeadlines()).blockFirst()
        then:
            headline != null
            headline.getData().getText().startsWith("Latest Headline")
    }
    // end::streamingClient[]

}
