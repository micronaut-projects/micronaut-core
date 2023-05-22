package io.micronaut.docs.server.response

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer

class StatusControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "httpstatus"))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test status"() {
            var response = client.toBlocking().exchange(HttpRequest.GET<Any>("/status"), String::class.java)
            var body = response.body

            response.status shouldBe HttpStatus.CREATED
            body.get() shouldBe "success"

            response = client.toBlocking().exchange(HttpRequest.GET<Any>("/status/http-response"), String::class.java)
            body = response.body

            response.status shouldBe HttpStatus.CREATED
            body.get() shouldBe "success"

            response = client.toBlocking().exchange(HttpRequest.GET<Any>("/status/http-status"), String::class.java)

            response.status shouldBe HttpStatus.CREATED
        }
    }

}
