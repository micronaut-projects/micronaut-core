/*
 * Copyright 2017 original authors
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

import io.micronaut.AbstractMicronautSpec
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.multipart.MultipartBody
import io.reactivex.Flowable
import spock.lang.Ignore

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class StreamUploadSpec extends AbstractMicronautSpec {

    void "test upload FileUpload object via transferTo"() {
        given:
        def data = '{"title":"Test"}'
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("title", "bar")
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, data.bytes)
                .build()


        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receiveFileUpload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE), String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def result = response.getBody().get()

        then:
        response.code() == HttpStatus.OK.code
        result == "Uploaded"
    }

    void "test upload big FileUpload object via transferTo"() {
        given:
        def val = 'Big ' + 'xxxx' * 500
        def data = '{"title":"' + val + '"}'

        MultipartBody requestBody = MultipartBody.builder()

                .addPart("title", "bar")
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, data.bytes)
                .build()


        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receiveFileUpload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

        def result = response.getBody().get()

        def file = new File(uploadDir, "bar.json")

        then:
        response.code() == HttpStatus.OK.code
        result == "Uploaded"
        file.exists()
        file.text == data
    }

    void "test non-blocking upload with publisher receiving bytes"() {
        given:
        def data = 'some data ' * 500
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, data.bytes)
                .addPart("title", "bar")
                .build()


        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receivePublisher", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))

        HttpResponse<String> response = flowable.blockingFirst()
        def result = response.getBody().get()

        then:
        response.code() == HttpStatus.OK.code
        result.length() == data.length()
        result == data

    }

    @Ignore
    void "test non-blocking upload with publisher receiving two objects"() {
        given:
        def data = '{"title":"Test"}'
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, data.bytes)
                .addPart("title", "bar")
                .build()


        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receiveTwoFlowParts", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))

        HttpResponse<String> response = flowable.blockingFirst()
        def result = response.getBody().get()

        then:
        response.code() == HttpStatus.OK.code
        result.length() == data.length()
        result == data

    }

    void "test non-blocking upload with publisher receiving converted JSON"() {
        given:
        def data = '{"title":"Test"}'
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, data.bytes)
                .addPart("title", "bar")
                .build()


        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/recieveFlowData", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.APPLICATION_JSON_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.getBody().get() == 'Data{title=\'Test\'}'

        when: "a large document with partial data is uploaded"
        def val = 'Big ' + 'xxxx' * 200
        data = '{"title":"' + val + '"}'
        requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE,data.bytes)
                .addPart("title", "bar")
                .build()
        flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/recieveFlowData", requestBody)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON_TYPE.TEXT_PLAIN_TYPE),
                String
        ))
        response = flowable.blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        ((String)response.getBody().get()).contains(val) // TODO: optimize this to use Jackson non-blocking and JsonNode


    }
}
