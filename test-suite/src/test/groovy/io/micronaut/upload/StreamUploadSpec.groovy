/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.http.HttpStatus
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import io.micronaut.AbstractMicronautSpec
import io.micronaut.http.HttpStatus
import spock.lang.Ignore

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class StreamUploadSpec extends AbstractMicronautSpec {

    //@Ignore
    void "test upload FileUpload object via transferTo"() {
        given:
        def data = '{"title":"Test"}'
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("title", "bar")
                .addFormDataPart("data", "data.json", RequestBody.create(MediaType.parse("application/json"), data))
                .build()


        when:
        def request = new Request.Builder()
                .url("$server/upload/receiveFileUpload")
                .post(requestBody)
        def response = client.newCall(
                request.build()
        ).execute()
        def result = response.body().string()

        then:
        response.code() == HttpStatus.OK.code
        result == "Uploaded"
    }

    //@Ignore
    void "test upload big FileUpload object via transferTo"() {
        given:
        def val = 'Big '+ 'xxxx' * 500
        def data = '{"title":"'+val+'"}'
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("title", "bar")
                .addFormDataPart("data", "data.json", RequestBody.create(MediaType.parse("application/json"), data))
                .build()


        when:
        def request = new Request.Builder()
                .url("$server/upload/receiveFileUpload")
                .post(requestBody)
        def response = client.newCall(
                request.build()
        ).execute()
        def result = response.body().string()

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
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("data", "data.json", RequestBody.create(MediaType.parse("text/plain"), data))
                .addFormDataPart("title", "bar")
                .build()


        when:
        def request = new Request.Builder()
                .url("$server/upload/receivePublisher")
                .post(requestBody)
        def response = client.newCall(
                request.build()
        ).execute()
        def result = response.body().string()

        then:
        response.code() == HttpStatus.OK.code
        result.length() == data.length()
        result == data

    }

    //@Ignore
    void "test non-blocking upload with publisher receiving two objects"() {
        given:
        def data = '{"title":"Test"}'
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("data", "data.json", RequestBody.create(MediaType.parse("application/json"), data))
                .addFormDataPart("title", "bar")
                .build()


        when:
        def request = new Request.Builder()
                .url("$server/upload/receiveTwoFlowParts")
                .post(requestBody)
        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == "bar: $data"

    }

    //@Ignore
    void "test non-blocking upload with publisher receiving converted JSON"() {
        given:
        def data = '{"title":"Test"}'
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("data", "data.json", RequestBody.create(MediaType.parse("application/json"), data))
                .addFormDataPart("title", "bar")
                .build()


        when:
        def request = new Request.Builder()
                .url("$server/upload/recieveFlowData")
                .post(requestBody)
        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == 'Data{title=\'Test\'}'

        when:"a large document with partial data is uploaded"
        def val = 'Big '+ 'xxxx' * 200
        data = '{"title":"'+val+'"}'
        requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("data", "data.json", RequestBody.create(MediaType.parse("application/json"), data))
                .addFormDataPart("title", "bar")
                .build()
        request = new Request.Builder()
                .url("$server/upload/recieveFlowData")
                .post(requestBody)
        response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string().contains(val) // TODO: optimize this to use Jackson non-blocking and JsonNode


    }
}
