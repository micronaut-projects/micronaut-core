package io.micronaut.docs.annotation.requestattributes

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono

class RequestAttributeSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    init {
        "test sender attributes" {
            val client = embeddedServer.applicationContext.getBean(StoryClient::class.java)
            val filter = embeddedServer.applicationContext.getBean(StoryClientFilter::class.java)

            val story = Mono.from(client.getById("jan2019")).block()
            val attributes = filter.latestRequestAttributes

            story shouldNotBe null
            attributes shouldNotBe null

            attributes.get("story-id") shouldBe "jan2019"
            attributes.get("client-name") shouldBe "storyClient"
            attributes.get("version") shouldBe "1"
        }
    }
}
