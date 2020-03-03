package io.micronaut.docs.server.endpoint

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.docs.server.intro.HelloControllerSpec
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.Test

import java.util.HashMap

import org.junit.Assert.assertEquals
import org.junit.Assert.fail

class MessageEndpointSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java,
                    mapOf("spec.name" to MessageEndpointSpec::class.java.simpleName, "endpoints.message.enabled" to true))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.url)
    )

    init {
        "test read message endpoint" {
            val response = client.exchange("/message", String::class.java).blockingFirst()

            response.code() shouldBe HttpStatus.OK.code
            response.body() shouldBe "default message"
        }

        "test write message endpoint" {
            var response = client.exchange(HttpRequest.POST<Map<String, Any>>("/message", mapOf("newMessage" to "A new message"))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED), String::class.java).blockingFirst()

            response.code() shouldBe HttpStatus.OK.code
            response.body() shouldBe "Message updated"
            response.contentType.get() shouldBe MediaType.TEXT_PLAIN_TYPE

            response = client.exchange("/message", String::class.java).blockingFirst()

            response.body() shouldBe "A new message"
        }

        "test delete message endpoint" {
            val response = client.exchange(HttpRequest.DELETE<Any>("/message"), String::class.java).blockingFirst()

            response.code() shouldBe HttpStatus.OK.code
            response.body() shouldBe "Message deleted"

            try {
                client.exchange("/message", String::class.java).blockingFirst()
            } catch (e: HttpClientResponseException) {
                e.status.code shouldBe 404
            } catch (e: Exception) {
                fail("Wrong exception thrown")
            }
        }
    }
}
