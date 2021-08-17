package io.micronaut.docs.server.upload

import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux

class UploadControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test file upload"() {
            val body = MultipartBody.builder()
                    .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, "{\"title\":\"Foo\"}".toByteArray())
                    .build()

            val flowable = Flux.from(client.exchange(
                    HttpRequest.POST("/upload", body)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.TEXT_PLAIN_TYPE),
                    String::class.java
            ))
            val response = flowable.blockFirst()

            response.status() shouldBe HttpStatus.OK
            response.body.get() shouldBe "Uploaded"
        }

        "test completed file upload"() {
            val body = MultipartBody.builder()
                    .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, "{\"title\":\"Foo\"}".toByteArray())
                    .build()

            val flowable = Flux.from(client!!.exchange(
                    HttpRequest.POST("/upload/completed", body)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.TEXT_PLAIN_TYPE),
                    String::class.java
            ))
            val response = flowable.blockFirst()

            response.status() shouldBe HttpStatus.OK
            response.body.get() shouldBe "Uploaded"
        }

        "test completed file upload with filename but no bytes"() {
            val body = MultipartBody.builder()
                    .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, ByteArray(0))
                    .build()

            val flowable = Flux.from(client!!.exchange(
                    HttpRequest.POST("/upload/completed", body)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.TEXT_PLAIN_TYPE),
                    String::class.java
            ))
            val response = flowable.blockFirst()

            response.status() shouldBe HttpStatus.OK
            response.body.get() shouldBe "Uploaded"
        }

        "test completed file upload with no name but with bytes"() {
            val body = MultipartBody.builder()
                    .addPart("file", "", MediaType.APPLICATION_JSON_TYPE, "{\"title\":\"Foo\"}".toByteArray())
                    .build()

            val flowable = Flux.from(client.exchange(
                    HttpRequest.POST("/upload/completed", body)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.TEXT_PLAIN_TYPE),
                    String::class.java
            ))

            val ex = shouldThrow<HttpClientResponseException> { flowable.blockFirst() }
            val response = ex.response
            val embedded: Map<*, *> = response.getBody(Map::class.java).get().get("_embedded") as Map<*, *>
            val message = ((embedded.get("errors") as java.util.List<*>).get(0) as Map<*, *>).get("message")

            message shouldBe "Required argument [CompletedFileUpload file] not specified"
        }

        "test completed file upload with no filename and no bytes"() {
            val body = MultipartBody.builder()
                    .addPart("file", "", MediaType.APPLICATION_JSON_TYPE, ByteArray(0))
                    .build()

            val flowable = Flux.from(client!!.exchange(
                    HttpRequest.POST("/upload/completed", body)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.TEXT_PLAIN_TYPE),
                    String::class.java
            ))

            val ex = shouldThrow<HttpClientResponseException> { flowable.blockFirst() }
            val response = ex.response
            val embedded: Map<*, *> = response.getBody(Map::class.java).get().get("_embedded") as Map<*, *>
            val message = ((embedded.get("errors") as java.util.List<*>).get(0) as Map<*, *>).get("message")

            message shouldBe "Required argument [CompletedFileUpload file] not specified"
        }

        "test completed file upload with no part"() {
            val body = MultipartBody.builder()
                    .addPart("filex", "", MediaType.APPLICATION_JSON_TYPE, ByteArray(0))
                    .build()

            val flowable = Flux.from(client!!.exchange(
                    HttpRequest.POST("/upload/completed", body)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.TEXT_PLAIN_TYPE),
                    String::class.java
            ))
            val ex = shouldThrow<HttpClientResponseException> { flowable.blockFirst() }
            val response = ex.response
            val embedded: Map<*, *> = response.getBody(Map::class.java).get().get("_embedded") as Map<*, *>
            val message = ((embedded.get("errors") as java.util.List<*>).get(0) as Map<*, *>).get("message")

            message shouldBe "Required argument [CompletedFileUpload file] not specified"
        }

        "test file bytes uploaded"() {
            val body = MultipartBody.builder()
                    .addPart("file", "file.json", MediaType.TEXT_PLAIN_TYPE, "some data".toByteArray())
                    .addPart("fileName", "bar")
                    .build()

            val flowable = Flux.from(client!!.exchange(
                    HttpRequest.POST("/upload/bytes", body)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.TEXT_PLAIN_TYPE),
                    String::class.java
            ))
            val response = flowable.blockFirst()

            response.status() shouldBe HttpStatus.OK
            response.body.get() shouldBe "Uploaded"
        }
    }
}
