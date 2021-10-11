/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.upload

import groovy.json.JsonSlurper
import io.micronaut.AbstractMicronautSpec
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import reactor.core.publisher.Flux
import spock.lang.IgnoreIf
import spock.lang.Retry

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Retry
class UploadSpec extends AbstractMicronautSpec {

    @Override
    Map<String, Object> getConfiguration() {
        ['micronaut.server.multipart.maxFileSize': '1KB']
    }

    void "test simple in-memory file upload with JSON"() {
        given:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, '{"title":"Foo"}'.bytes)
                .addPart("title", "bar")
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/receive-json", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.getBody().get() == 'bar: Data{title=\'Foo\'}'
    }

    void "test simple in-memory file upload with JSON with multiple files"() {
        given:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, '{"title":"Foo"}'.bytes)
                .addPart("data", "bar.json", MediaType.APPLICATION_JSON_TYPE, '{"title":"Bar"}'.bytes)
                .addPart("title", "bar")
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/receive-json", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockFirst()

        then: "the second file is ignored"
        response.code() == HttpStatus.OK.code
        response.getBody().get() == 'bar: Data{title=\'Foo\'}'
    }

    void "test simple in-memory file upload with invalid JSON"() {
        given:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, '{"title":"Foo"'.bytes)
                .addPart("title", "bar")
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/receive-json", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN),
                String
        ))

        flowable.blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.BAD_REQUEST


        when:
        def json = new JsonSlurper().parseText(e.response.getBody().get())

        then:
        json._embedded.errors[0].message.contains("Failed to convert argument [data]")
        json._embedded.errors[0].path == "/data"

    }

    void "test simple in-memory file upload "() {
        given:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.TEXT_PLAIN_TYPE, 'some data'.bytes)
                .addPart("title", "bar")
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/receive-plain", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.getBody().get() == 'bar: some data'
    }

    void "test file upload with wrong argument name for file"() {
        given:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("wrong-name", "data.json", MediaType.TEXT_PLAIN_TYPE, 'some data'.bytes)
                .addPart("title", "bar")
                .build()

        when:
        Flux<HttpResponse> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/receive-plain", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        def resp = flowable.blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.BAD_REQUEST

        when:
        def json = new JsonSlurper().parseText(e.response.getBody().get())

        then:
        json._embedded.errors[0].message == "Required argument [String data] not specified"
    }

    void "test file upload with wrong argument name for simple part"() {
        given:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.TEXT_PLAIN_TYPE, 'some data'.bytes)
                .addPart("wrong-name", "bar")
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/receive-plain", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        flowable.blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.BAD_REQUEST

        when:
        def json = new JsonSlurper().parseText(e.response.getBody().get())

        then:
        json._embedded.errors[0].message == "Required argument [String title] not specified"
    }

    void "test file upload with missing argument for simple part"() {
        given:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.TEXT_PLAIN_TYPE, 'some data'.bytes)
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/receive-plain", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        flowable.blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.BAD_REQUEST

        when:
        def json = new JsonSlurper().parseText(e.response.getBody().get())

        then:
        json._embedded.errors[0].message == "Required argument [String title] not specified"
    }

    void "test file upload with missing argument for file part"() {
        given:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("title", "bar")
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/receive-plain", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        flowable.blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.BAD_REQUEST

        when:
        def json = new JsonSlurper().parseText(e.response.getBody().get())

        then:
        json._embedded.errors[0].message == "Required argument [String data] not specified"
    }

    void "test file upload to byte array"() {
        given:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.TEXT_PLAIN_TYPE, 'some data'.bytes)
                .addPart("title", "bar")
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/receive-bytes", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.getBody().get() == 'bar: 9'
    }

    @IgnoreIf({ env["GITHUB_WORKFLOW"] })
    void "test simple in-memory file upload exceeds size"() {
        given:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.TEXT_PLAIN_TYPE, ('some data' * 1000).bytes)
                .addPart("title", "bar")
                .build()

        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/receive-plain", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        flowable.blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.REQUEST_ENTITY_TOO_LARGE

        when:
        def json = new JsonSlurper().parseText(e.response.getBody().get())

        then:
        json._embedded.errors[0].message.contains("exceeds the maximum allowed content length [1024]")
    }

    void "test upload CompletedFileUpload object"() {
        given:
        def data = '{"title":"Test"}'
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("title", "bar")
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, data.bytes)
                .build()


        when:
        Flux<HttpResponse<String>> flowable = Flux.from(client.exchange(
                HttpRequest.POST("/upload/receive-completed-file-upload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockFirst()
        def result = response.getBody().get()

        then:
        response.code() == HttpStatus.OK.code
        result == 'data.json: 16'
    }

    void "test the error condition using a mono"() {
        given:
        def data = '{"title":"Test"}'
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, data.bytes)
                .build()

        when:
        HttpResponse<?> response = Flux.from(client.exchange(
                HttpRequest.POST("/upload/receive-multipart-body-as-mono", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                Argument.STRING,
                Argument.mapOf(String, Object)
        )).onErrorResume(t -> {
            if (t instanceof HttpClientResponseException) {
                return Flux.just(((HttpClientResponseException) t).response)
            }
            throw t
        }).blockFirst()

        then:
        response.status() == HttpStatus.OK
        response.getBody(String).get() == "OK"
    }
}
