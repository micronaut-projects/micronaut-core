package io.micronaut.http.server.upload

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux

class KotlinUploadControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to KotlinUploadControllerSpec::class.simpleName))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test file upload with kotlin flow"() {
            val body = MultipartBody.builder()
                .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, "{\"title\":\"Foo\"}".toByteArray())
                .build()

            val flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/flow", body)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.TEXT_PLAIN_TYPE),
                Int::class.java
            ))
            val response = flowable.blockFirst()

            response.status() shouldBe HttpStatus.OK
            response.body.get() shouldBe 15
        }

        "test file upload with kotlin await"() {
            val body = MultipartBody.builder()
                .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, "{\"title\":\"Foo\"}".toByteArray())
                .build()

            val flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/await", body)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.TEXT_PLAIN_TYPE),
                Int::class.java
            ))
            val response = flowable.blockFirst()

            response.status() shouldBe HttpStatus.OK
            response.body.get() shouldBe 15
        }
    }
}
