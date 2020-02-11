package io.micronaut.docs.client.upload

// tag::imports[]
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient

import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable

// end::imports[]
// tag::multipartBodyImports[]
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.http.multipart.StreamingFileUpload
import io.micronaut.http.client.multipart.MultipartBody
import org.reactivestreams.Publisher
// end::multipartBodyImports[]
// tag::controllerImports[]
import io.micronaut.http.annotation.Controller
// end::controllerImports[]

// tag::spockImports[]
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

// end::spockImports[]

// tag::class[]
class MultipartFileUploadSpec extends Specification {
// end::class[]

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

    static final File uploadDir = File.createTempDir()

    void setupSpec() {
        uploadDir.mkdir()
        assert uploadDir.exists() && uploadDir.canWrite()
    }

    void cleanup() {
        uploadDir.listFiles()*.delete()
    }

    void cleanupSpec() {
        uploadDir.delete()
    }

    void "test multipart file request byte[]"() {
        given:
        // tag::file[]
        File file = new File(uploadDir, "data.txt")
        file.text = "test file"
        file.createNewFile()

        // end::file[]

        // tag::multipartBody[]
        MultipartBody requestBody = MultipartBody.builder()     // <1>
                .addPart(                                       // <2>
                    "data",
                    file.name,
                    MediaType.TEXT_PLAIN_TYPE,
                    file
                ).build()                                       // <3>

        // end::multipartBody[]

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(

                // tag::request[]
                HttpRequest.POST("/multipart/upload", requestBody)       // <1>
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE) // <2>
                // end::request[]
                        .accept(MediaType.TEXT_PLAIN_TYPE),

                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody().get()

        then:
        body == "Uploaded 9 bytes"
    }

    void "test multipart file request byte[] with content type"() {

        // tag::multipartBodyBytes[]
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "sample.txt", MediaType.TEXT_PLAIN_TYPE, "test content".bytes)
                .build()

        // end::multipartBodyBytes[]

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/multipart/upload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody().get()

        then:
        body == "Uploaded 12 bytes"
    }

    void "test multipart file request byte[] without content type"() {
        given:
        File file = new File(uploadDir, "data.txt")
        file.text = "test file"
        file.createNewFile()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/multipart/upload", MultipartBody.builder().addPart("data", file.name, file.bytes))
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody().get()

        then:
        body == "Uploaded 9 bytes"
    }

    void "test upload FileUpload object via CompletedFileUpload"() {
        given:
        File file = new File(uploadDir, "walkingthehimalayas.txt")
        file.text = "test file"
        file.createNewFile()

        when:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", file)
                .addPart("title", "Walking The Himalayas")
                .build()

        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/multipart/complete-file-upload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody().get()
        def newFile = new File(uploadDir, "Walking The Himalayas.txt")

        then:
        body == "Uploaded 9 bytes. File size: 9"
        newFile.exists()
        newFile.text == file.text

    }

    void "test upload InputStream"() {
        given:
        File file = new File(uploadDir, "walkingthehimalayas.txt")
        file.createNewFile()
        file << "test file input stream"

        when:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", file.name, MediaType.TEXT_PLAIN_TYPE, file.newInputStream(), file.length())
                .addPart("title", "Walking The Himalayas")
                .build()

        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/multipart/complete-file-upload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody().get()
        def newFile = new File(uploadDir, "Walking The Himalayas.txt")

        then:
        body == "Uploaded ${file.length()} bytes. File size: ${file.length()}"
        newFile.exists()
        newFile.text == file.text

    }

    void "test upload InputStream without ContentType"() {
        given:
        File file = new File(uploadDir, "walkingthehimalayas.txt")
        file.createNewFile()
        file << "test file input stream"

        when:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", file.name, file.newInputStream(), file.length())
                .addPart("title", "Walking The Himalayas")
                .build()

        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/multipart/complete-file-upload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody().get()
        def newFile = new File(uploadDir, "Walking The Himalayas.txt")

        then:
        body == "Uploaded ${file.length()} bytes. File size: ${file.length()}"
        newFile.exists()
        newFile.text == file.text

    }

    @Controller('/multipart')
    static class MultipartController {


        @Post(value = '/upload', consumes = MediaType.MULTIPART_FORM_DATA)
        HttpResponse<String> upload(byte[] data) {
            return HttpResponse.ok("Uploaded " + data.length + " bytes")
        }

        @Post(value = '/complete-file-upload', consumes = MediaType.MULTIPART_FORM_DATA)
        Publisher<HttpResponse> completeFileUpload(CompletedFileUpload data, String title) {
            File newFile = new File(uploadDir, title + ".txt")
            newFile.createNewFile()
            newFile.append(data.getInputStream())
            return Flowable.just(HttpResponse.ok("Uploaded ${newFile.length()} bytes. File size: ${data.getSize()}"))
        }

        @Post(value = '/stream-file-upload', consumes = MediaType.MULTIPART_FORM_DATA)
        Publisher<HttpResponse> streamFileUpload(StreamingFileUpload data, String title) {
            return Flowable.fromPublisher(data.transferTo(new File(uploadDir, title + ".txt"))).map ({success->
                success ? HttpResponse.ok("Uploaded") :
                        HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Something bad happened")
            })
        }

    }
}
