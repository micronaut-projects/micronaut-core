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

import io.micronaut.AbstractMicronautSpec
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.multipart.MultipartBody
import io.reactivex.Flowable

/**
 * Any changes or additions to this test should also be done
 * in {@link StreamUploadSpec} and {@link DiskUploadSpec}
 */
class MixedUploadSpec extends AbstractMicronautSpec {

    void "test upload FileUpload object via transferTo"() {
        given:
        def data = '{"title":"Test"}'
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("title", "bar-mixed")
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, data.bytes)
                .build()


        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-file-upload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE), String
        ))
        HttpResponse<String> response = flowable.blockingFirst()
        def result = response.getBody().get()
        File file = new File(uploadDir, "bar-mixed.json")
        file.deleteOnExit()

        then:
        response.code() == HttpStatus.OK.code
        result == "Uploaded ${data.size()}"
        file.exists()
        file.length() == data.size()
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
                HttpRequest.POST("/upload/receive-file-upload", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

        def result = response.getBody().get()

        def file = new File(uploadDir, "bar.json")

        then:
        response.code() == HttpStatus.OK.code
        result == "Uploaded ${data.size()}"
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
                HttpRequest.POST("/upload/receive-publisher", requestBody)
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


    void "test non-blocking upload with publisher receiving part datas"() {
        given:
        def data = 'some data ' * 500
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, data.bytes)
                .addPart("title", "bar")
                .build()


        when:
        Flowable<HttpResponse<Long>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-partdata", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                Long
        ))

        HttpResponse<Long> response = flowable.blockingFirst()
        def result = response.getBody().get()

        then:
        response.code() == HttpStatus.OK.code
        result == new Long(data.length())
    }

    void "test non-blocking upload with publisher receiving two objects"() {
        given:
        def data = '{"title":"Test"}'
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, data.bytes)
                .addPart("title", "bar")
                .build()


        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-two-flow-parts", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))

        HttpResponse<String> response = flowable.blockingFirst()
        def result = response.getBody().get()

        then:
        response.code() == HttpStatus.OK.code
        result.length() == 21
        result == "bar: $data"

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
                HttpRequest.POST("/upload/receive-flow-data", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN),
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
                HttpRequest.POST("/upload/receive-flow-data", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.APPLICATION_JSON_TYPE.TEXT_PLAIN_TYPE),
                String
        ))
        response = flowable.blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        ((String)response.getBody().get()).contains(val) // TODO: optimize this to use Jackson non-blocking and JsonNode
    }

    void "test non-blocking upload with publisher receiving multiple converted JSON"() {
        given:
        def data = '{"title":"Test"}'
        def data2 = '{"title":"Test2"}'
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE, data.bytes)
                .addPart("data", "data2.json", MediaType.APPLICATION_JSON_TYPE, data2.bytes)
                .addPart("title", "bar")
                .build()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-multiple-flow-data", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.getBody().get() == '[Data{title=\'Test\'}, Data{title=\'Test2\'}]'

        when: "a large document with partial data is uploaded"
        def val = 'xxxx' * 200
        data = '{"title":"Big ' + val + '"}'
        data2 = '{"title":"Big2 ' + val + '"}'
        requestBody = MultipartBody.builder()
                .addPart("data", "data.json", MediaType.APPLICATION_JSON_TYPE,data.bytes)
                .addPart("data", "data2.json", MediaType.APPLICATION_JSON_TYPE,data2.bytes)
                .addPart("title", "bar")
                .build()
        flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-multiple-flow-data", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.APPLICATION_JSON_TYPE.TEXT_PLAIN_TYPE),
                String
        ))
        response = flowable.blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body().contains('Data{title=\'Big xx')
        response.body().contains('Data{title=\'Big2 xx')
    }

    void "test receiving multiple completed parts with the same name"() {
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "abc.txt", MediaType.TEXT_PLAIN_TYPE, "abc".bytes)
                .addPart("data", "def.txt", MediaType.TEXT_PLAIN_TYPE, "abcdef".bytes)
                .addPart("title", "bar")
                .build()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-multiple-completed", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.APPLICATION_JSON_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == '{"files":[{"name":"abc.txt","size":3},{"name":"def.txt","size":6}],"title":"bar"}'
    }

    void "test receiving multiple streaming parts with the same name"() {
        def val = ('Big ' + 'xxxx' * 20000).bytes
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "abc.txt", MediaType.TEXT_PLAIN_TYPE, val)
                .addPart("data", "def.txt", MediaType.TEXT_PLAIN_TYPE, val)
                .addPart("title", "bar")
                .build()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-multiple-streaming", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == (val.length * 2).toString()
    }

    void "test receiving a publisher of publishers with the same name"() {
        def val = ('Big ' + 'xxxx' * 200).bytes
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", "abc.txt", MediaType.TEXT_PLAIN_TYPE, val)
                .addPart("data", "def.txt", MediaType.TEXT_PLAIN_TYPE, val)
                .addPart("title", "bar")
                .build()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-multiple-publishers", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == (val.length * 2).toString()
    }

    void "test receiving a flowable that controls flow with a large file"() {
        def val = ('Big ' + 'xxxx' * 200000).bytes
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("json", "abc.json", MediaType.TEXT_JSON_TYPE, '{"hello": "world"}'.bytes)
                .addPart("file", "def.txt", MediaType.TEXT_PLAIN_TYPE, val)
                .addPart("title", "bar")
                .build()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-flow-control", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == (val.length).toString()
    }

    void "test receiving a flowable that controls flow with a large attribute"() {
        def val = ('Big ' + 'xxxx' * 200000)
        MultipartBody requestBody = MultipartBody.builder()
                .addPart("data", val)
                .build()

        when:
        Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                HttpRequest.POST("/upload/receive-big-attribute", requestBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.TEXT_PLAIN_TYPE),
                String
        ))
        HttpResponse<String> response = flowable.blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == val
    }

    Map<String, Object> getConfiguration() {
        super.getConfiguration() << ['micronaut.http.client.read-timeout': 300,
                                     'micronaut.server.multipart.mixed': true,
                                     'micronaut.server.multipart.threshold': 20000]
    }
}
