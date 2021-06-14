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
package io.micronaut.http.server.netty.binding

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec

class RegexBindingSpec extends AbstractMicronautSpec {

    void "test regex matches"() {
        when:
        HttpResponse response = rxClient.toBlocking().exchange(HttpRequest.GET("/test-binding/regex/blue"))

        then:
        response.status() == HttpStatus.OK
    }

    void "test regex does not match"() {
        when:
        rxClient.toBlocking().exchange(HttpRequest.GET("/test-binding/regex/yellow"))

        then:
        def e = thrown(HttpClientResponseException)
        e.response.status == HttpStatus.NOT_FOUND
    }

    @Controller('/test-binding')
    @Requires(property = 'spec.name', value = 'RegexBindingSpec')
    static class TestController {

        @Get('/regex/{color:^blue|orange$}')
        HttpStatus regex(String color) {
            HttpStatus.OK
        }
    }
}
