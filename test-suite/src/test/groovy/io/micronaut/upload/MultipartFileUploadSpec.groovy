package io.micronaut.upload

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest

// tag::imports[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.multipart.StreamingFileUpload
import io.micronaut.http.server.netty.multipart.CompletedFileUpload
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
// end::completedImports[]

class MultipartFileUploadSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
    @Shared @AutoCleanup HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

    static final File uploadDir = File.createTempDir()

    void cleanup() {
        uploadDir.listFiles()*.delete()
    }

    void cleanupSpec() {
        uploadDir.delete()
    }

    void "test multipart file request byte[]"() {
        given:
        File file = new File(uploadDir, "data.txt")
        file.text = "test file"
        file.createNewFile()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/multipart/upload", MultipartBody.builder().addPart("data", file.name, MediaType.TEXT_PLAIN_TYPE, file))
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody().get()

        then:
        body == "Uploaded 9 bytes"
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
                HttpRequest.POST("/multipart/completeFileUpload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody().get()
        def newFile = new File(uploadDir, "Walking The Himalayas.txt")

        then:
        body == "Uploaded 9 bytes"
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
                HttpRequest.POST("/multipart/completeFileUpload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody().get()
        def newFile = new File(uploadDir, "Walking The Himalayas.txt")

        then:
        body == "Uploaded ${file.length()} bytes"
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
                HttpRequest.POST("/multipart/completeFileUpload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def body = response.getBody().get()
        def newFile = new File(uploadDir, "Walking The Himalayas.txt")

        then:
        body == "Uploaded ${file.length()} bytes"
        newFile.exists()
        newFile.text == file.text

    }


    @Controller('/multipart')
    static class MultipartController {


        @Post(uri = '/upload', consumes = MediaType.MULTIPART_FORM_DATA)
        HttpResponse<String> upload(byte[] data) {
            return HttpResponse.ok("Uploaded " + data.length + " bytes")
        }

        @Post(consumes = MediaType.MULTIPART_FORM_DATA)
        Publisher<HttpResponse> completeFileUpload(CompletedFileUpload data, String title) {
            File newFile = new File(uploadDir, title + ".txt")
            newFile.createNewFile()
            newFile.append(data.getInputStream())
            return Flowable.just(HttpResponse.ok("Uploaded ${newFile.length()} bytes"))
        }

        @Post(consumes = MediaType.MULTIPART_FORM_DATA)
        Publisher<HttpResponse> streamFileUpload(StreamingFileUpload data, String title) {
            return Flowable.fromPublisher(data.transferTo(new File(uploadDir, title + ".txt"))).map ({success->
                success ? HttpResponse.ok("Uploaded") :
                HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Something bad happened")
            })
        }

    }
}
