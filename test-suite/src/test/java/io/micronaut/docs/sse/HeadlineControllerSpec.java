package io.micronaut.docs.sse;

import io.micronaut.context.ApplicationContext;
import io.micronaut.docs.streaming.Headline;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HeadlineControllerSpec {

    // tag::streamingClient[]
    @Test
    public void testClientAnnotationStreaming() throws Exception {
        try( EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class) ) {
            HeadlineClient headlineClient = embeddedServer
                    .getApplicationContext()
                    .getBean(HeadlineClient.class);

            Event<Headline> headline = headlineClient.streamHeadlines().blockingFirst();

            assertNotNull( headline );
            assertTrue( headline.getData().getText().startsWith("Latest Headline") );
        }
    }
    // end::streamingClient[]

}
