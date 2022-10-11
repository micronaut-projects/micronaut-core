package io.micronaut.upload

import io.micronaut.AbstractMicronautSpec
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import reactor.core.publisher.Flux

class MultipartDisabledSpec extends AbstractMicronautSpec {

    @Override
    Map<String, Object> getConfiguration() {
        ['micronaut.server.multipart.enabled': false]
    }

    void "test simple in-memory file upload with JSON"() {
        given:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, '{"title":"Foo"}'.bytes)
                .addPart("title", "bar")
                .build()

        when:
        HttpResponse<?> response = Flux.from(client.exchange(
                HttpRequest.POST("/upload/receive-json", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        then:
        response.code() == HttpStatus.UNSUPPORTED_MEDIA_TYPE.code
    }
}
