package io.micronaut.upload

import io.micronaut.AbstractMicronautSpec
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.netty.multipart.MultipartBody
import io.reactivex.Flowable
import spock.lang.Issue

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/2568")
class InvalidTransferSpec extends AbstractMicronautSpec {

    @Override
    Map<String, Object> getConfiguration() {
        ['micronaut.server.multipart.location': '/does/not/exist']
    }

    void "test simple in-memory file upload with JSON"() {
        given:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, '{"title":"Foo"}'.bytes)
                .addPart("title", "bar")
                .build()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-file-upload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        flowable.blockingFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.code() == HttpStatus.INTERNAL_SERVER_ERROR.code
        ex.message == 'Something bad happened'
    }

}
