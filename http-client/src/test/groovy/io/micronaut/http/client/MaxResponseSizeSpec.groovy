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


import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.ContentLengthExceededException
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@MicronautTest
@Property(name = "micronaut.http.client.maxContentLength", value = '1kb' )
class MaxResponseSizeSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    void "test max content length setting"() {
        when:
        client.toBlocking().retrieve(HttpRequest.GET('/max'), String)

        then:
        def e = thrown(ContentLengthExceededException)
        e.message == 'The received length exceeds the maximum allowed content length [1024]'
    }

    @Controller("/max")
    static class GetController {

        @Get(value = "/", produces = MediaType.TEXT_PLAIN)
        String index() {
            return ("success" * 1000)
        }
    }
}
