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
package io.micronaut.http.server.netty.binding

import groovy.json.JsonSlurper
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class DefaultJsonErrorHandlingSpec extends AbstractMicronautSpec {

    void "test simple string-based body parsing with invalid JSON"() {

        when:
        def json = '{"title":"The Stand"'
        rxClient.exchange(
                HttpRequest.POST('/errors/string', json), String
        ).blockingFirst()


        then:
        def e = thrown(HttpClientResponseException)
        e.message == """Invalid JSON: Unexpected end-of-input
 at [Source: UNKNOWN; line: 1, column: 21]"""
        e.response.status == HttpStatus.BAD_REQUEST

        when:
        def body = e.response.getBody(String).orElse(null)
        def result = new JsonSlurper().parseText(body)

        then:
        result['_links'].self.href == '/errors/string'
        result.message.startsWith('Invalid JSON')
    }

    @Controller("/errors")
    static class ErrorsController {
        @Post("/string")
        String string(@Body String text) {
            "Body: ${text}"
        }

        @Post("/map")
        String map(@Body Map<String, Object> json) {
            "Body: ${json}"
        }
    }
}
