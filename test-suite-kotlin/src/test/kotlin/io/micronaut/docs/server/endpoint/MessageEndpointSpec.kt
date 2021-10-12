package io.micronaut.docs.server.endpoint

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer

import org.junit.Assert.fail
import reactor.core.publisher.Flux

class MessageEndpointSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java,
                    mapOf("spec.name" to MessageEndpointSpec::class.java.simpleName, "endpoints.message.enabled" to true))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url)
    )

    init {
        "test read message endpoint" {
            val response = client.toBlocking().exchange("/message", String::class.java)

            response.code() shouldBe HttpStatus.OK.code
            response.body() shouldBe "default message"
        }

        "test write message endpoint" {
            var response = Flux.from(client.exchange(HttpRequest.POST<Map<String, Any>>("/message", mapOf("newMessage" to "A new message"))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED), String::class.java)).blockFirst()

            response.code() shouldBe HttpStatus.OK.code
            response.body() shouldBe "Message updated"
            response.contentType.get() shouldBe MediaType.TEXT_PLAIN_TYPE

            response = client.toBlocking().exchange("/message", String::class.java)

            response.body() shouldBe "A new message"
        }

        "test delete message endpoint" {
            val response = client.toBlocking().exchange(HttpRequest.DELETE<Any>("/message"), String::class.java)

            response.code() shouldBe HttpStatus.OK.code
            response.body() shouldBe "Message deleted"

            try {
                client.toBlocking().exchange("/message", String::class.java)
            } catch (e: HttpClientResponseException) {
                e.status.code shouldBe 404
            } catch (e: Exception) {
                fail("Wrong exception thrown")
            }
        }
    }
}
