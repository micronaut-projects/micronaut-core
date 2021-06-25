package io.micronaut.docs.server.endpoint

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux

import java.util.Date

class CurrentDateEndpointSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url)
    )

    init {
        "test read custom date endpoint" {
            val response = Flux.from(client.exchange("/date", String::class.java)).blockFirst()

            response.code() shouldBe HttpStatus.OK.code
        }

        "test read custom date endpoint with argument" {
            val response = Flux.from(client.exchange("/date/current_date_is", String::class.java)).blockFirst()

            response.code() shouldBe HttpStatus.OK.code
            response.body()!!.startsWith("current_date_is: ") shouldBe true
        }

        // issue https://github.com/micronaut-projects/micronaut-core/issues/883
        "test read with produces" {
            val response = Flux.from(client.exchange("/date/current_date_is", String::class.java)).blockFirst()

            response.contentType.get() shouldBe MediaType.TEXT_PLAIN_TYPE
        }

        "test write custom date endpoint" {
            val originalDate: Date
            val resetDate: Date

            var response = Flux.from(client.exchange("/date", String::class.java)).blockFirst()
            originalDate = Date(java.lang.Long.parseLong(response.body()!!))

            response =  Flux.from(client.exchange(HttpRequest.POST<Map<String, Any>>("/date", mapOf()), String::class.java)).blockFirst()

            response.code() shouldBe HttpStatus.OK.code
            response.body() shouldBe "Current date reset"

            response =  Flux.from(client.exchange("/date", String::class.java)).blockFirst()
            resetDate = Date(java.lang.Long.parseLong(response.body()!!))

            assert(resetDate.time > originalDate.time)
        }

        "test disable endpoint" {
            embeddedServer.stop() // top the previously created server otherwise a port conflict will occur

            val server = ApplicationContext.run(EmbeddedServer::class.java, mapOf("custom.date.enabled" to false))
            val rxClient = server.applicationContext.createBean(HttpClient::class.java, server.url)

            try {
                Flux.from(rxClient.exchange("/date", String::class.java)).blockFirst()
            } catch (ex: HttpClientResponseException) {
                ex.response.code() shouldBe HttpStatus.NOT_FOUND.code
            }

            server.close()
        }
    }
}
