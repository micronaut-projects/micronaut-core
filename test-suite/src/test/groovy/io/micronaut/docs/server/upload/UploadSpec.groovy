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
package io.micronaut.docs.server.upload

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class UploadSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer) // <1>

    void "test file upload"() {
        given:
        OkHttpClient client = new OkHttpClient()
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "file.json",
                    RequestBody.create(MediaType.parse("application/json"), '{"title":"Foo"}')
                )
                .build()

        Request.Builder request = new Request.Builder()
                .url(new URL(embeddedServer.getURL(), "/upload"))
                .post(body)// <2>
        Response response = client.newCall(request.build()).execute()

        expect:
        response.code() == HttpStatus.OK.code
        response.body().string() == "Uploaded" // <2>
    }

    void "test completed file upload"() {
        given:
        OkHttpClient client = new OkHttpClient()
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "file.json",
                RequestBody.create(MediaType.parse("application/json"), '{"title":"Foo"}')
        )
                .build()

        Request.Builder request = new Request.Builder()
                .url(new URL(embeddedServer.getURL(), "/upload/completed"))
                .post(body)
        Response response = client.newCall(request.build()).execute()

        expect:
        response.code() == HttpStatus.OK.code
        response.body().string() == "Uploaded"
    }

    void "test file bytes upload"() {
        given:
        OkHttpClient client = new OkHttpClient()
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "file.json", RequestBody.create(MediaType.parse("text/plain"), 'some data'))
                .addFormDataPart("fileName", "bar")
                .build()

        Request.Builder request = new Request.Builder()
                .url(new URL(embeddedServer.getURL(), "/upload/bytes"))
                .post(body)// <2>
        Response response = client.newCall(request.build()).execute()

        expect:
        response.code() == HttpStatus.OK.code
        response.body().string() == "Uploaded"
    }
}
