package io.micronaut.docs.annotation

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono

import javax.validation.ConstraintViolationException

import java.lang.Exception

class PetControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url)
    )

    init {
        "test post pet" {
            val client = embeddedServer.applicationContext.getBean(PetClient::class.java)

            // tag::post[]
            val pet = Mono.from(client.save("Dino", 10)).block()

            pet.name shouldBe "Dino"
            pet.age.toLong() shouldBe 10
            // end::post[]
        }

        "test post pet validation" {
            val client = embeddedServer.applicationContext.getBean(PetClient::class.java)

            // tag::error[]
            try {
                Mono.from(client.save("Fred", -1)).block()
            } catch (e: Exception) {
                e.javaClass shouldBe ConstraintViolationException::class.java
                e.message shouldBe "save.age: must be greater than or equal to 1"
            }
            // end::error[]
        }
    }

    // tag::errorRule[]
    // end::errorRule[]
}
