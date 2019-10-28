package io.micronaut.docs.client.upload

// tag::imports[]
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Post
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import io.micronaut.http.client.RxHttpClient
import java.io.File
import java.io.FileWriter

// end::imports[]
// tag::multipartBodyImports[]
import io.micronaut.http.client.multipart.MultipartBody
// end::multipartBodyImports[]
// tag::controllerImports[]
import io.micronaut.http.annotation.Controller
// end::controllerImports[]

// tag::class[]
class MultipartFileUploadSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.url)
    )

    init {
        "test multipart file request byte[]" {
            // tag::file[]
            val toWrite = "test file"
            val file = File.createTempFile("data", ".txt")
            val writer = FileWriter(file)
            writer.write(toWrite)
            writer.close()
            // end::file[]

            // tag::multipartBody[]
            val requestBody = MultipartBody.builder()     // <1>
                    .addPart(                             // <2>
                            "data",
                            file.name,
                            MediaType.TEXT_PLAIN_TYPE,
                            file
                    ).build()                             // <3>

            // end::multipartBody[]

            val flowable = Flowable.fromPublisher(client!!.exchange(

                    // tag::request[]
                    HttpRequest.POST("/multipart/upload", requestBody)   // <1>
                            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE) // <2>
                            // end::request[]
                            .accept(MediaType.TEXT_PLAIN_TYPE),

                    String::class.java
            ))
            val response = flowable.blockingFirst()
            val body = response.body.get()

            body shouldBe "Uploaded 9 bytes"
        }

        "test multipart file request byte[] with ContentType" {
            // tag::multipartBodyBytes[]
            val requestBody = MultipartBody.builder()
                    .addPart("data", "sample.txt", MediaType.TEXT_PLAIN_TYPE, "test content".toByteArray())
                    .build()

            // end::multipartBodyBytes[]

            val flowable = Flowable.fromPublisher(client!!.exchange(
                    HttpRequest.POST("/multipart/upload", requestBody)
                            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                            .accept(MediaType.TEXT_PLAIN_TYPE),
                    String::class.java
            ))
            val response = flowable.blockingFirst()
            val body = response.body.get()

            body shouldBe "Uploaded 12 bytes"
        }

        "test multipart file request byte[] without ContentType" {
            val toWrite = "test file"
            val file = File.createTempFile("data", ".txt")
            val writer = FileWriter(file)
            writer.write(toWrite)
            writer.close()
            file.createNewFile()

            val flowable = Flowable.fromPublisher(client!!.exchange<MultipartBody.Builder, String>(
                    HttpRequest.POST<MultipartBody.Builder>("/multipart/upload", MultipartBody.builder().addPart("data", file.name, file))
                            .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                            .accept(MediaType.TEXT_PLAIN_TYPE),
                    String::class.java
            ))
            val response = flowable.blockingFirst()
            val body = response.body.get()

            body shouldBe "Uploaded 9 bytes"
        }
    }

    @Controller("/multipart")
    internal class MultipartController {

        @Post(value = "/upload", consumes = [MediaType.MULTIPART_FORM_DATA])
        fun upload(data: ByteArray): HttpResponse<String> {
            return HttpResponse.ok("Uploaded " + data.size + " bytes")
        }
    }
}
