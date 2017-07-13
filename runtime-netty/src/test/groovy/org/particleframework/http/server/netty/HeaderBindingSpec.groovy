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

import okhttp3.Request
import org.particleframework.http.HttpHeaders
import org.particleframework.http.MediaType
import org.particleframework.http.binding.annotation.Header
import org.particleframework.stereotype.Controller
import org.particleframework.web.router.annotation.Get

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class HeaderBindingSpec extends AbstractParticleSpec {

    void "test bind HTTP headers"() {
        expect:
        client.newCall(
                new Request.Builder()
                        .url("$server$uri")
                        .header("Content-Type", "application/json")
                        .build()
        ).execute().body().string() == result

        where:
        uri                 | result
        '/header/multiple'  | "Header: [application/json]"
        '/header/simple'    | "Header: application/json"
        '/header/withValue' | "Header: application/json"
//        '/header/withMediaType' | "Header: application/json" TODO
//        '/header/all' | "Header: application/json" TODO

    }

    @Controller
    static class HeaderController {

        @Get
        String simple(@Header String contentType) {
            "Header: $contentType"
        }

        @Get
        String multiple(@Header List<String> contentType) {
            "Header: $contentType"
        }

        @Get
        String withValue(@Header(HttpHeaders.CONTENT_TYPE) String contentType) {
            "Header: $contentType"
        }

        @Get
        String withValue(@Header MediaType contentType) {
            "Header: $contentType"
        }

        @Get
        String all(HttpHeaders httpHeaders) {
            "Header: ${httpHeaders.get(HttpHeaders.CONTENT_TYPE)}"
        }
    }
}
