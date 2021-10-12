package io.micronaut.docs.server.upload

import io.micronaut.AbstractMicronautSpec
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import reactor.core.publisher.Flux

class UploadSpec extends AbstractMicronautSpec {

    void cleanup() {
        File file = File.createTempFile("file.json", "temp")
        file.delete()
    }

    void "test file upload"() {
        given:
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, '{"title":"Foo"}'.bytes)
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.getBody().get() == "Uploaded"
    }

    void "test file upload output stream"() {
        given:
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, '{"title":"Foo"}'.bytes)
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/outputStream", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.getBody().get() == "Uploaded"
    }

    void "test completed file upload"() {
        given:
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, '{"title":"Foo"}'.bytes)
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/completed", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.getBody().get() == "Uploaded"
    }

    void "test completed file upload with filename but no bytes"() {
        given:
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "file.json", MediaType.APPLICATION_JSON_TYPE, new byte[0])
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/completed", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.getBody().get() == "Uploaded"
    }

    void "test completed file upload with no filename but with bytes"() {
        given:
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "", MediaType.APPLICATION_JSON_TYPE, '{"title":"Foo"}'.bytes)
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/completed", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message == "Required argument [CompletedFileUpload file] not specified"
    }

    void "test completed file upload with no file name and no bytes"() {
        given:
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "", MediaType.APPLICATION_JSON_TYPE, new byte[0])
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/completed", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message == "Required argument [CompletedFileUpload file] not specified"
    }

    void "test completed file upload with no part"() {
        given:
        MultipartBody body = MultipartBody.builder()
                .addPart("filex", "", MediaType.APPLICATION_JSON_TYPE, new byte[0])
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/completed", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message == "Required argument [CompletedFileUpload file] not specified"
    }

    void "test file bytes upload"() {
        given:
        MultipartBody body = MultipartBody.builder()
                .addPart("file", "file.json", MediaType.TEXT_PLAIN_TYPE, 'some data'.bytes)
                .addPart("fileName", "bar")
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/bytes", body)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.getBody().get() == "Uploaded"
    }
}
