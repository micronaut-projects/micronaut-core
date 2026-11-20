package io.micronaut.docs.server.filters.bodybuffer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import java.security.MessageDigest
import java.util.HexFormat

class ChecksumFilterSpec : StringSpec() {

    val embeddedServer = autoClose(
        ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "ChecksumFilterSpec"))
    )

    val client = autoClose(
        embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url)
    )

    init {
        "the filter reads the body and the controller reads it again" {
            val body = "x".repeat(256 * 1024)
            val response = client.toBlocking().retrieve(HttpRequest.POST("/checksum", body)
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header("X-Content-SHA256", sha256(body)))
            response shouldBe "Received ${body.length} characters"
        }

        "a mismatch is rejected" {
            val e = shouldThrow<HttpClientResponseException> {
                client.toBlocking().retrieve(HttpRequest.POST("/checksum", "hello")
                    .contentType(MediaType.TEXT_PLAIN_TYPE)
                    .header("X-Content-SHA256", "wrong"))
            }
            e.status shouldBe HttpStatus.BAD_REQUEST
        }
    }

    private fun sha256(text: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))
}
