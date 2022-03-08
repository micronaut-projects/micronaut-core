package io.micronaut.upload

import io.micronaut.AbstractMicronautSpec
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.multipart.MultipartBody
import io.netty.handler.codec.http.multipart.DiskFileUpload
import reactor.core.publisher.Flux

class NoLocationTransferSpec extends AbstractMicronautSpec {

    @Override
    Map<String, Object> getConfiguration() {
        // leave micronaut.server.multipart.location unset
        [:]
    }

    void "test simple in-memory file upload with JSON"() {
        given:
        DiskFileUpload.baseDirectory = null

        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, '{"title":"Foo"}'.bytes)
                .addPart("title", "bar")
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/receive-file-upload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        def response = flowable.blockFirst()

        then:
        response.code() == HttpStatus.OK.code
    }

}
