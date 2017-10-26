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
package org.particleframework.http.server.netty.upload

import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import org.particleframework.http.HttpHeaders
import org.particleframework.http.HttpStatus
import org.particleframework.http.server.netty.AbstractParticleSpec
import spock.lang.Ignore
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class StreamUploadSpec extends AbstractParticleSpec {


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

        then:
        response.code() == HttpStatus.OK.code
        response.body().string().length() == data.length()

    }

    @Ignore // not yet implemented
    void "test non-blocking upload with publisher receiving converted JSON"() {
        given:
        def data = '{"title:"Test"}'
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
        response.headers().get(HttpHeaders.CONTENT_TYPE) == "application/json"
        response.body().string() == ''

    }
}
