package io.micronaut.docs.server.request

import io.kotlintest.shouldNotBe
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer

class MessageControllerSpec: StringSpec() {

    val embeddedServer = autoClose( // <2>
            ApplicationContext.run(EmbeddedServer::class.java) // <1>
    )

    val client = autoClose( // <2>
            embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.getURL()) // <1>
    )

    init {
        "test message controller"() {
            val body = client.toBlocking().retrieve("/request/hello?name=John")

            body shouldNotBe null
            body shouldBe "Hello John!!"
        }
    }

}
