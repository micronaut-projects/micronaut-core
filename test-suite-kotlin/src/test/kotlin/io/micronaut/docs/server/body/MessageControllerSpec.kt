package io.micronaut.docs.server.body

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer

class MessageControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test echo response"() {
            val body = "My Text"
            val response = client.toBlocking().retrieve(
                    HttpRequest.POST("/receive/echo", body)
                            .contentType(MediaType.TEXT_PLAIN_TYPE), String::class.java)

            response shouldBe body
        }

        "test echo reactive response"() {
            val body = "My Text"
            val response = client.toBlocking().retrieve(
                    HttpRequest.POST("/receive/echo-flow", body)
                            .contentType(MediaType.TEXT_PLAIN_TYPE), String::class.java)

            response shouldBe body
        }
    }
}
