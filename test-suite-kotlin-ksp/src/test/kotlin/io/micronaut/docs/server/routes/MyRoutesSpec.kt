package io.micronaut.docs.server.routes

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer

class MyRoutesSpec: StringSpec() {

    val embeddedServer = autoClose( // <2>
            ApplicationContext.run(EmbeddedServer::class.java) // <1>
    )

    val client = autoClose( // <2>
            embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.getURL()) // <1>
    )

    init {
        "test custom route" {
            val body = client.toBlocking().retrieve("/issues/show/12") // <3>

            body shouldNotBe null
            body shouldBe "Issue # 12!" // <4>
        }
    }
}
