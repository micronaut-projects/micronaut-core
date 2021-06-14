package io.micronaut.docs.server.routes

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.ReactorHttpClient
import io.micronaut.runtime.server.EmbeddedServer

class MyRoutesSpec: StringSpec() {

    val embeddedServer = autoClose( // <2>
            ApplicationContext.run(EmbeddedServer::class.java) // <1>
    )

    val client = autoClose( // <2>
            embeddedServer.applicationContext.createBean(ReactorHttpClient::class.java, embeddedServer.getURL()) // <1>
    )

    init {
        "test custom route" {
            val body = client.toBlocking().retrieve("/issues/show/12") // <3>

            body shouldNotBe null
            body shouldBe "Issue # 12!" // <4>
        }
    }
}
