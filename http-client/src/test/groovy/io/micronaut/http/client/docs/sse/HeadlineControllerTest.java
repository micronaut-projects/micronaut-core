package io.micronaut.http.client.docs.sse;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.client.docs.streaming.Headline;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.Test;
import reactor.core.publisher.Mono;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HeadlineControllerTest {

    // tag::streamingClient[]
    @Test
    public void testClientAnnotationStreaming() throws Exception {
        try( EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class) ) {
            HeadlineClient headlineClient = embeddedServer
                    .getApplicationContext()
                    .getBean(HeadlineClient.class);

            Event<Headline> headline = headlineClient.streamHeadlines().blockFirst();

            assertNotNull( headline );
            assertTrue( headline.getData().getText().startsWith("Latest Headline") );
        }
    }
    // end::streamingClient[]

}
