package io.micronaut.docs.annotation.headers

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono

class HeaderSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java, mapOf("pet.client.id" to "11") )
    )

    init {
        "test sender headers" {
            val client = embeddedServer.applicationContext.getBean(PetClient::class.java)

            val pet = Mono.from(client["Fred"]).block()

            pet shouldNotBe null
            pet.age.toLong() shouldBe 11
        }
    }
}
