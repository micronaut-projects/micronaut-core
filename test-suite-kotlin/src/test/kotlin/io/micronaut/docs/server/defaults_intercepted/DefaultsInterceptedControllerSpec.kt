package io.micronaut.docs.server.defaults_intercepted

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer

class DefaultsInterceptedControllerSpec : StringSpec() {

    val embeddedServer = autoClose(
        ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "defaults-intercepted"))
    )

    val client = autoClose(
        embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test echo response"() {
            val response1 = client.toBlocking().retrieve(
                HttpRequest.POST("/defaults-intercepted/echo", "My Text")
                    .header("MYHEADER", "abc123")
                    .contentType(MediaType.TEXT_PLAIN_TYPE), String::class.java
            )

            response1 shouldBe "abc123"

            val response2 = client.toBlocking().retrieve(
                HttpRequest.POST("/defaults-intercepted/echo", "My Text")
                    .contentType(MediaType.TEXT_PLAIN_TYPE), String::class.java
            )

            response2 shouldBe "THEDEFAULT"
        }

        "test echo reactive response"() {
            val response1 = client.toBlocking().retrieve(
                HttpRequest.POST("/defaults-intercepted/echo-publisher", "My Text")
                    .header("MYHEADER", "abc123")
                    .contentType(MediaType.TEXT_PLAIN_TYPE), String::class.java
            )

            response1 shouldBe "abc123"

            val response2 = client.toBlocking().retrieve(
                HttpRequest.POST("/defaults-intercepted/echo-publisher", "My Text")
                    .contentType(MediaType.TEXT_PLAIN_TYPE), String::class.java
            )

            response2 shouldBe "THEDEFAULT"
        }
    }
}
