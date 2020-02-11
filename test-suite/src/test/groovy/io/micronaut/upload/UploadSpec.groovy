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
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.netty.multipart.MultipartBody
import io.reactivex.Flowable
import spock.lang.IgnoreIf

/**
 * @author Graeme Rocher
 * @since 1.0
 */
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
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-json", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

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
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-json", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

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
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-json", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.APPLICATION_JSON_TYPE),
                String
        ))

        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.BAD_REQUEST


        when:
        def json = new JsonSlurper().parseText(e.response.getBody().get())

        then:
        json.message.contains("Failed to convert argument [data]")
        json.path == "/data"

    }

    void "test simple in-memory file upload "() {
        given:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.TEXT_PLAIN_TYPE, 'some data'.bytes)
                .addPart("title", "bar")
                .build()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-plain", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

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
        Flowable<HttpResponse> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-plain", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        def resp = flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.BAD_REQUEST

        when:
        def json = new JsonSlurper().parseText(e.response.getBody().get())

        then:
        json.message == "Required argument [String data] not specified"
    }

    void "test file upload with wrong argument name for simple part"() {
        given:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.TEXT_PLAIN_TYPE, 'some data'.bytes)
                .addPart("wrong-name", "bar")
                .build()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-plain", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.BAD_REQUEST

        when:
        def json = new JsonSlurper().parseText(e.response.getBody().get())

        then:
        json.message == "Required argument [String title] not specified"
    }

    void "test file upload with missing argument for simple part"() {
        given:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.TEXT_PLAIN_TYPE, 'some data'.bytes)
                .build()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-plain", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.BAD_REQUEST

        when:
        def json = new JsonSlurper().parseText(e.response.getBody().get())

        then:
        json.message == "Required argument [String title] not specified"
    }

    void "test file upload with missing argument for file part"() {
        given:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("title", "bar")
                .build()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-plain", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.BAD_REQUEST

        when:
        def json = new JsonSlurper().parseText(e.response.getBody().get())

        then:
        json.message == "Required argument [String data] not specified"
    }

    void "test file upload to byte array"() {
        given:
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.TEXT_PLAIN_TYPE, 'some data'.bytes)
                .addPart("title", "bar")
                .build()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-bytes", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

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
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-plain", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        flowable.blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.REQUEST_ENTITY_TOO_LARGE

        when:
        def json = new JsonSlurper().parseText(e.response.getBody().get())

        then:
        json.message.contains("exceeds the maximum allowed content length [1024]")
    }

    void "test upload CompletedFileUpload object"() {
        given:
        def data = '{"title":"Test"}'
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("title", "bar")
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, data.bytes)
                .build()


        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-completed-file-upload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def result = response.getBody().get()

        then:
        response.code() == HttpStatus.OK.code
        result == 'data.json: 16'
    }

}
