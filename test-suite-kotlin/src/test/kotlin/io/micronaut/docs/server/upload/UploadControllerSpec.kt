package io.micronaut.docs.server.upload

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.netty.multipart.MultipartBody
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable

class UploadControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test file upload"() {
            val body = MultipartBody.builder()
                    .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, "{\"title\":\"Foo\"}".toByteArray())
                    .build()

            val flowable = Flowable.fromPublisher(client.exchange(
                    HttpRequest.POST("/upload", body)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.TEXT_PLAIN_TYPE),
                    String::class.java
            ))
            val response = flowable.blockingFirst()

            response.status() shouldBe HttpStatus.OK
            response.body.get() shouldBe "Uploaded"
        }

        "test completed file upload"() {
            val body = MultipartBody.builder()
                    .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, "{\"title\":\"Foo\"}".toByteArray())
                    .build()

            val flowable = Flowable.fromPublisher(client!!.exchange(
                    HttpRequest.POST("/upload/completed", body)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.TEXT_PLAIN_TYPE),
                    String::class.java
            ))
            val response = flowable.blockingFirst()

            response.status() shouldBe HttpStatus.OK
            response.body.get() shouldBe "Uploaded"
        }

        "test completed file upload with filename but no bytes"() {
            val body = MultipartBody.builder()
                    .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, ByteArray(0))
                    .build()

            val flowable = Flowable.fromPublisher(client!!.exchange(
                    HttpRequest.POST("/upload/completed", body)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.TEXT_PLAIN_TYPE),
                    String::class.java
            ))
            val response = flowable.blockingFirst()

            response.status() shouldBe HttpStatus.OK
            response.body.get() shouldBe "Uploaded"
        }

        "test completed file upload with no name but with bytes"() {
            val body = MultipartBody.builder()
                    .addPart("file", "", MediaType.APPLICATION_JSON_TYPE, "{\"title\":\"Foo\"}".toByteArray())
                    .build()

            val flowable = Flowable.fromPublisher(client.exchange(
                    HttpRequest.POST("/upload/completed", body)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.TEXT_PLAIN_TYPE),
                    String::class.java
            ))

            val ex = shouldThrow<HttpClientResponseException> { flowable.blockingFirst() }

            ex.message shouldBe "Required argument [CompletedFileUpload file] not specified"
        }

        "test completed file upload with no filename and no bytes"() {
            val body = MultipartBody.builder()
                    .addPart("file", "", MediaType.APPLICATION_JSON_TYPE, ByteArray(0))
                    .build()

            val flowable = Flowable.fromPublisher(client!!.exchange(
                    HttpRequest.POST("/upload/completed", body)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.TEXT_PLAIN_TYPE),
                    String::class.java
            ))

            val ex = shouldThrow<HttpClientResponseException> { flowable.blockingFirst() }

            ex.message shouldBe "Required argument [CompletedFileUpload file] not specified"
        }

        "test completed file upload with no part"() {
            val body = MultipartBody.builder()
                    .addPart("filex", "", MediaType.APPLICATION_JSON_TYPE, ByteArray(0))
                    .build()

            val flowable = Flowable.fromPublisher(client!!.exchange(
                    HttpRequest.POST("/upload/completed", body)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.TEXT_PLAIN_TYPE),
                    String::class.java
            ))
            val ex = shouldThrow<HttpClientResponseException> { flowable.blockingFirst() }

            ex.message shouldBe "Required argument [CompletedFileUpload file] not specified"
        }

        "test file bytes uploaded"() {
            val body = MultipartBody.builder()
                    .addPart("file", "file.json", MediaType.TEXT_PLAIN_TYPE, "some data".toByteArray())
                    .addPart("fileName", "bar")
                    .build()

            val flowable = Flowable.fromPublisher(client!!.exchange(
                    HttpRequest.POST("/upload/bytes", body)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.TEXT_PLAIN_TYPE),
                    String::class.java
            ))
            val response = flowable.blockingFirst()

            response.status() shouldBe HttpStatus.OK
            response.body.get() shouldBe "Uploaded"
        }
    }
}
