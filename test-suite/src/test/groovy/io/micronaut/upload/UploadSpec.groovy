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

import groovy.json.JsonSlurper
import io.micronaut.http.HttpStatus
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import io.micronaut.AbstractMicronautSpec
import io.micronaut.http.HttpStatus

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class UploadSpec extends AbstractMicronautSpec {

    @Override
    Map<String, Object> getConfiguration() {
        ['micronaut.server.multipart.maxFileSize':'1KB']
    }

    void "test simple in-memory file upload with JSON"() {
        given:
        RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("data", "data.json", RequestBody.create(MediaType.parse("application/json"), '{"title":"Foo"}'))
                    .addFormDataPart("title", "bar")
                    .build()

        when:
        def request = new Request.Builder()
                .url("$server/upload/receiveJson")
                .post(requestBody)
        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == 'bar: Data{title=\'Foo\'}'

    }

    void "test simple in-memory file upload with invalid JSON"() {
        given:
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("data", "data.json", RequestBody.create(MediaType.parse("application/json"), '{"title":"Foo"'))
                .addFormDataPart("title", "bar")
                .build()

        when:
        def request = new Request.Builder()
                .url("$server/upload/receiveJson")
                .post(requestBody)
        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.BAD_REQUEST.code

        when:
        def json = new JsonSlurper().parseText(response.body().string())

        then:
        json.message.contains("Failed to convert argument [data]")
        json.path == "/data"

    }

    void "test simple in-memory file upload "() {
        given:
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("data", "data.json", RequestBody.create(MediaType.parse("text/plain"), 'some data'))
                .addFormDataPart("title", "bar")
                .build()

        when:
        def request = new Request.Builder()
                .url("$server/upload/receivePlain")
                .post(requestBody)
        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == 'bar: some data'
    }

    void "test file upload with wrong argument name for file"() {
        given:
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("datax", "data.json", RequestBody.create(MediaType.parse("text/plain"), 'some data'))
                .addFormDataPart("title", "bar")
                .build()

        when:
        def request = new Request.Builder()
                .url("$server/upload/receivePlain")
                .post(requestBody)
        def response = client.newCall(
                request.build()
        ).execute()
        def body = response.body().string()
        def json = new JsonSlurper().parseText(body)

        then:
        response.code() == HttpStatus.BAD_REQUEST.code
        json.message == "Required argument [String data] not specified"
    }

    void "test file upload with wrong argument name for simple part"() {
        given:
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("data", "data.json", RequestBody.create(MediaType.parse("text/plain"), 'some data'))
                .addFormDataPart("titlex", "bar")
                .build()

        when:
        def request = new Request.Builder()
                .url("$server/upload/receivePlain")
                .post(requestBody)
        def response = client.newCall(
                request.build()
        ).execute()
        def body = response.body().string()
        def json = new JsonSlurper().parseText(body)

        then:
        response.code() == HttpStatus.BAD_REQUEST.code
        json.message == "Required argument [String title] not specified"
    }

    void "test file upload with missing argument for simple part"() {
        given:
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("data", "data.json", RequestBody.create(MediaType.parse("text/plain"), 'some data'))
                .build()

        when:
        def request = new Request.Builder()
                .url("$server/upload/receivePlain")
                .post(requestBody)
        def response = client.newCall(
                request.build()
        ).execute()
        def body = response.body().string()
        def json = new JsonSlurper().parseText(body)

        then:
        response.code() == HttpStatus.BAD_REQUEST.code
        json.message == "Required argument [String title] not specified"
    }

    void "test file upload with missing argument for file part"() {
        given:
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("title", "bar")
                .build()

        when:
        def request = new Request.Builder()
                .url("$server/upload/receivePlain")
                .post(requestBody)
        def response = client.newCall(
                request.build()
        ).execute()
        def body = response.body().string()
        def json = new JsonSlurper().parseText(body)

        then:
        response.code() == HttpStatus.BAD_REQUEST.code
        json.message == "Required argument [String data] not specified"
    }

    void "test file upload to byte array"() {
        given:
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("data", "data.json", RequestBody.create(MediaType.parse("text/plain"), 'some data'))
                .addFormDataPart("title", "bar")
                .build()

        when:
        def request = new Request.Builder()
                .url("$server/upload/receiveBytes")
                .post(requestBody)
        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == 'bar: 9'
    }

    void "test simple in-memory file upload exceeds size"() {
        given:
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("data", "data.json", RequestBody.create(MediaType.parse("text/plain"), 'some data' * 1000))
                .addFormDataPart("title", "bar")
                .build()

        when:
        def request = new Request.Builder()
                .url("$server/upload/receivePlain")
                .post(requestBody)
        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.REQUEST_ENTITY_TOO_LARGE.code
        response.message() =='Request Entity Too Large'
        def body = response.body().string()

        when:
        def json = new JsonSlurper().parseText(body)

        then:
        json.message.contains("exceeds the maximum content length [1024]")
    }

    void "test upload CompletedFileUpload object"() {
        given:
        def data = '{"title":"Test"}'
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("title", "bar")
                .addFormDataPart("data", "data.json", RequestBody.create(MediaType.parse("application/json"), data))
                .build()


        when:
        def request = new Request.Builder()
                .url("$server/upload/receiveCompletedFileUpload")
                .post(requestBody)
        def response = client.newCall(
                request.build()
        ).execute()
        def result = response.body().string()

        then:
        response.code() == HttpStatus.OK.code
        result == 'data.json'
    }
}
