package io.micronaut.docs.annotation.headers

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.docs.annotation.Pet
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.Assert
import org.junit.Test

import java.util.Collections

class HeaderSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java, mapOf("pet.client.id" to "11") )
    )

    init {
        "test sender headers" {
            val client = embeddedServer.applicationContext.getBean(PetClient::class.java)

            val pet = client["Fred"].blockingGet()

            pet shouldNotBe null
            pet.age.toLong() shouldBe 11
        }
    }
}
