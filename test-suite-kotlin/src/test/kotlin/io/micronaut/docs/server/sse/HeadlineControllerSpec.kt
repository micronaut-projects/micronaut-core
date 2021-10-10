package io.micronaut.docs.server.sse

import io.kotlintest.eventually
import io.kotlintest.seconds
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.sse.RxSseClient
import io.micronaut.http.sse.Event
import io.micronaut.runtime.server.EmbeddedServer
import org.opentest4j.AssertionFailedError

import java.util.ArrayList

class HeadlineControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    init {
        "test consume eventstream object" {
            val client = embeddedServer.applicationContext.createBean(RxSseClient::class.java, embeddedServer.url)

            val events = ArrayList<Event<Headline>>()

            client.eventStream(HttpRequest.GET<Any>("/headlines"), Headline::class.java).subscribe {
                events.add(it)
            }

            eventually(2.seconds, AssertionFailedError::class.java) {
                events.size shouldBe 2
                events[0].data.title shouldBe "Micronaut 1.0 Released"
                events[0].data.description shouldBe "Come and get it"
            }
        }
    }
}