package io.micronaut.docs.sse

import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer

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

            val headline = headlineClient.streamHeadlines().blockFirst()

            assertNotNull(headline)
            assertTrue(headline!!.data.text!!.startsWith("Latest Headline"))
        }
        // end::streamingClient[]
    }
}
