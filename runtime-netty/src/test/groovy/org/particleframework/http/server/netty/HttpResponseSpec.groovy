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
package org.particleframework.http.server.netty

import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.particleframework.http.HttpMessage
import org.particleframework.http.HttpResponse
import org.particleframework.http.HttpStatus
import org.particleframework.stereotype.Controller
import org.particleframework.web.router.annotation.Get

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class HttpResponseSpec extends AbstractParticleSpec {

    void "test ok status"() {
        when:
        def request = new Request.Builder()
                .url("$server/response/ok")
                .get()
        then:
        client.newCall(
                request.build()
        ).execute().code() == HttpStatus.OK.code
    }


    void "test ok status with body"() {
        when:
        def request = new Request.Builder()
                .url("$server/response/okWithBody")
                .get()
        def response = client.newCall(
                request.build()
        ).execute()
        then:
        response.code() == HttpStatus.OK.code
        response.body().string() == 'some text'
    }

    void "test custom status"() {
        when:
        def request = new Request.Builder()
                .url("$server/response/status")
                .get()
        then:
        client.newCall(
                request.build()
        ).execute().code() == HttpStatus.MOVED_PERMANENTLY.code
    }

    @Controller
    static class ResponseController {

        @Get
        HttpResponse ok() {
            HttpResponse.ok()
        }

        @Get
        HttpResponse okWithBody() {
            HttpResponse.ok("some text")
        }

        @Get
        HttpMessage status() {
            HttpResponse.status(HttpStatus.MOVED_PERMANENTLY)
        }
    }
}
