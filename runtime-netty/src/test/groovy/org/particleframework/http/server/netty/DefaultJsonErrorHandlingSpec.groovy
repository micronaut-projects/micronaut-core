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
import org.particleframework.http.HttpStatus
import org.particleframework.http.binding.annotation.Body
import org.particleframework.stereotype.Controller
import org.particleframework.web.router.annotation.Post

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class DefaultJsonErrorHandlingSpec extends AbstractParticleSpec {

    void "test simple string-based body parsing with invalid JSON"() {

        when:
        def json = '{"title":The Stand}'
        def request = new Request.Builder()
                .url("$server/errors/string")
                .header("Content-Length", json.length().toString())
                .post(RequestBody.create(MediaType.parse("application/json"), json))

        def response = client.newCall(
                request.build()
        ).execute()

        then:
        response.code() == HttpStatus.BAD_REQUEST.code
        response.message() == "Invalid JSON"

    }

    @Controller
    static class ErrorsController {
        @Post
        String string(@Body String text) {
            "Body: ${text}"
        }

        @Post
        String map(@Body Map<String, Object> json) {
            "Body: ${json}"
        }
    }
}
