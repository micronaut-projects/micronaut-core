package io.micronaut.docs.server.sse

import io.kotest.assertions.timing.eventually
import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.sse.SseClient
import io.micronaut.http.sse.Event
import io.micronaut.runtime.server.EmbeddedServer
import org.opentest4j.AssertionFailedError
import reactor.core.publisher.Flux

import java.util.ArrayList
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

@ExperimentalTime
class HeadlineControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    init {
        "test consume eventstream object" {
            val client = embeddedServer.applicationContext.createBean(SseClient::class.java, embeddedServer.url)

            val events = ArrayList<Event<Headline>>()

            Flux.from(client.eventStream(HttpRequest.GET<Any>("/headlines"), Headline::class.java)).subscribe {
                events.add(it)
            }

            eventually(2.toDuration(DurationUnit.SECONDS), AssertionFailedError::class) {
                events.size shouldBe 2
                events[0].data.title shouldBe "Micronaut 1.0 Released"
                events[0].data.description shouldBe "Come and get it"
            }
        }
    }
}
