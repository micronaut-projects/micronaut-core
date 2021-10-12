package io.micronaut.docs.datavalidation.pogo

import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer

class EmailControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
        ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "datavalidationpogo"))
    )

    val client = autoClose(
        embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url)
    )

    init {
        //tag::pojovalidated[]
        "test pojo validation" {
            val e = shouldThrow<HttpClientResponseException> {
                val email = Email()
                email.subject = "Hi"
                email.recipient = ""
                client.toBlocking().exchange<Email, Any>(HttpRequest.POST("/email/send", email))
            }
            var response = e.response

            response.status shouldBe HttpStatus.BAD_REQUEST

            val email = Email()
            email.subject = "Hi"
            email.recipient = "me@micronaut.example"
            response = client.toBlocking().exchange<Email, Any>(HttpRequest.POST("/email/send", email))

            response.status shouldBe HttpStatus.OK
        }
        //end::pojovalidated[]
    }
}
