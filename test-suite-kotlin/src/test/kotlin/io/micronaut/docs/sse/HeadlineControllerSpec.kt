package io.micronaut.docs.sse

import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.docs.streaming.Headline
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.sse.Event
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.Test

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

class HeadlineControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    init {
        // tag::streamingClient[]
        "test client annotations streaming" {
            val headlineClient = embeddedServer
                    .applicationContext
                    .getBean(HeadlineClient::class.java)

            val headline = headlineClient.streamHeadlines().blockingFirst()

            assertNotNull(headline)
            assertTrue(headline!!.data.text!!.startsWith("Latest Headline"))
        }
        // end::streamingClient[]
    }
}
