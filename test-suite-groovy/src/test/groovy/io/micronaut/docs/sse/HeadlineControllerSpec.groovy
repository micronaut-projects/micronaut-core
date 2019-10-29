package io.micronaut.docs.sse

import io.micronaut.context.ApplicationContext
import io.micronaut.docs.streaming.Headline
import io.micronaut.http.sse.Event
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.Test
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HeadlineControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    // tag::streamingClient[]
    @Test
    void testClientAnnotationStreaming() throws Exception {
        when:
            HeadlineClient headlineClient = embeddedServer
                    .getApplicationContext()
                    .getBean(HeadlineClient.class)

            Event<Headline> headline = headlineClient.streamHeadlines().blockFirst()
        then:
            headline != null
            headline.getData().getText().startsWith("Latest Headline")
    }
    // end::streamingClient[]

}
