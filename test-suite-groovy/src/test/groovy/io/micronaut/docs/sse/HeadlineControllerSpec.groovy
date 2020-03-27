package io.micronaut.docs.sse


import io.micronaut.docs.streaming.Headline
import io.micronaut.http.sse.Event
import io.micronaut.test.annotation.MicronautTest
import org.junit.Test
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class HeadlineControllerSpec extends Specification {

    // tag::streamingClient[]
    @Inject HeadlineClient headlineClient

    @Test
    void testClientAnnotationStreaming() throws Exception {
        when:
            Event<Headline> headline = headlineClient.streamHeadlines().blockingFirst()
        then:
            headline != null
            headline.getData().getText().startsWith("Latest Headline")
    }
    // end::streamingClient[]

}
