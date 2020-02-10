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
package io.micronaut.http.client


import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class MultiHeaderSpec extends Specification {

    @Inject
    @Client("/")
    RxHttpClient asyncClient

    void "test multi-valued header"() {
        given:
        BlockingHttpClient client = asyncClient.toBlocking()

        when:
        HttpRequest request = HttpRequest.GET(path)
        request.getHeaders().add("multi", "a").add("multi", "b")
        HttpResponse<String> response = client.exchange(
                request,
                String
        )

        then:
        response.body() == expected

        where:
        path | expected
        "/echo-multi-header-as-list/from-request" | "[a, b]"
        "/echo-multi-header-as-list/from-list-param" | "[a, b]"
        "/echo-multi-header-as-list/from-array-param" | "[a, b]"
        "/echo-multi-header-as-list/from-string-param" | "a"
    }

    @Controller("/echo-multi-header-as-list")
    static class EchoMultiHeaderController {

        @Get(uri = "/from-request", produces = MediaType.TEXT_PLAIN)
        String fromRequest(HttpRequest request) {
            request.headers.getAll("multi")
        }

        @Get(uri = "/from-list-param", produces = MediaType.TEXT_PLAIN)
        String fromListParam(@Header List<String> multi) {
            multi
        }

        @Get(uri = "/from-array-param", produces = MediaType.TEXT_PLAIN)
        String fromArrayParam(@Header String[] multi) {
            multi
        }

        @Get(uri = "/from-string-param", produces = MediaType.TEXT_PLAIN)
        String fromStringParam(@Header String multi) {
            multi
        }
    }
}
