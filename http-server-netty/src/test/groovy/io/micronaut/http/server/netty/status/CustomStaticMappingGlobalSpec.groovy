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
package io.micronaut.http.server.netty.status

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.*
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import reactor.core.publisher.Flux

/**
 * Tests the global attribute for @Error
 */
class CustomStaticMappingGlobalSpec extends AbstractMicronautSpec {

    void "test that a bad request is handled is handled by a globally marked controller method"() {
        when:
        rxClient.toBlocking().exchange('/test1/bad')

        then:
        HttpClientResponseException e = thrown()
        e.response.code() == HttpStatus.BAD_REQUEST.code
        e.response.reason() == "You sent me bad stuff - from Test2Controller.badHandler()"

        when:
        rxClient.toBlocking().exchange('/test2/bad')

        then:
        e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code
        e.response.reason() == "You sent me bad stuff - from Test2Controller.badHandler()"
    }

    void "test that a bad request response for invalid request data can be handled by a globally marked controller method"() {
        when:
        Flux.from(rxClient.exchange(
                HttpRequest.POST('/test1/simple', [name:"Fred"])
                        .contentType(MediaType.FORM)
        )).blockFirst()

        then:
        HttpClientResponseException e = thrown()
        e.response.code() == HttpStatus.BAD_REQUEST.code
        e.response.reason() == "You sent me bad stuff - from Test2Controller.badHandler()"

        when:
        rxClient.exchange(
                HttpRequest.POST('/test2/simple', [name:"Fred"])
                        .contentType(MediaType.FORM)
        ).blockFirst()

        then:
        e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code
        e.response.reason() == "You sent me bad stuff - from Test2Controller.badHandler()"
    }

    void "test that a not found response request data can be handled by a local method"() {
        when:
        rxClient.exchange('/test1/notFound').blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.NOT_FOUND.code
        e.response.reason() == "We cannot find anything - from Test2Controller.notFoundHandler()"
    }

    @Controller('/test1')
    @Requires(property = 'spec.name', value = 'CustomStaticMappingGlobalSpec')
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED )
    static class Test1Controller {
        @Get('/bad')
        HttpResponse bad() {
            HttpResponse.badRequest()
        }

        @Post('/simple')
        String simple(String name, Integer age) {
            "name: $name, age: $age"
        }

        @Get('/not-found')
        HttpResponse notFound() {
            null // return a null to simulate a query is not found
        }
    }

    @Controller('/test2')
    @Requires(property = 'spec.name', value = 'CustomStaticMappingGlobalSpec')
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED )
    static class Test2Controller {
        @Get('/bad')
        HttpResponse bad() {
            HttpResponse.badRequest()
        }

        @Post('/simple')
        String simple(String name, Integer age) {
            "name: $name, age: $age"
        }

        @Error(status = HttpStatus.BAD_REQUEST, global = true)
        HttpResponse badHandler(HttpRequest request) {
            HttpResponse.status(HttpStatus.BAD_REQUEST, "You sent me bad stuff - from Test2Controller.badHandler()")
        }

        @Error(status = HttpStatus.NOT_FOUND, global = true)
        HttpResponse notFoundHandler(HttpRequest request) {
            HttpResponse.status(HttpStatus.NOT_FOUND, "We cannot find anything - from Test2Controller.notFoundHandler()")
        }
    }
}
