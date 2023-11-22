package io.micronaut.docs.sse

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldStartWith
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer

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

            headline.shouldNotBeNull()
            headline.data.text!!.shouldStartWith("Latest Headline")
        }
        // end::streamingClient[]
    }
}
