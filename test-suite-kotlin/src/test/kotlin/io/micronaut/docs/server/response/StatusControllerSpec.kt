package io.micronaut.docs.server.response

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer

class StatusControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "httpstatus"))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.getURL())
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
