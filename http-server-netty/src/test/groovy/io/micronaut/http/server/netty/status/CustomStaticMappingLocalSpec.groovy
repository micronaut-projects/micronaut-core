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
 * Test the non global attribute for @Error.
 * Created by graemerocher on 13/09/2017.
 */
class CustomStaticMappingLocalSpec extends AbstractMicronautSpec {

    void "test that a bad request is handled is handled by the locally marked controller"() {
        when:
        rxClient.exchange('/test1/bad').blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code
        e.response.reason() == "You sent me bad stuff - from Test1Controller.badHandler()"

        when:
        rxClient.exchange('/test2/bad').blockFirst()

        then:
        e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code
        e.response.reason() == "You sent me bad stuff - from Test2Controller.badHandler()"
    }

    void "test that a bad request response for invalid request data can be redirected by the router to the local method"() {
        when:
        rxClient.exchange(
                HttpRequest.POST('/test1/simple', [name:"Fred"])
                           .contentType(MediaType.FORM)
        ).blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code
        e.response.reason() == "You sent me bad stuff - from Test1Controller.badHandler()"

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
        rxClient.exchange('/test1/not-found').blockFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.NOT_FOUND.code
        e.response.reason() == "We cannot find anything - from Test1Controller.notFoundHandler()"
    }

    /**
     * This fails currently
     */
    void "test that a unsupported media type is handled by a local method"() {
        when:
        HttpResponse<String> response = rxClient.exchange(
                HttpRequest.POST('/test1/simple', '<foo></foo>')
                        .contentType(MediaType.APPLICATION_XML),
                String
        ).onErrorResume(t -> Flux.just(((HttpClientResponseException) t).response))
        .blockFirst()

        then:
        response.getBody(String).get() == "You sent an unsupported media type - from Test1Controller.unsupportedMediaTypeHandler()"
    }

    @Controller('/test1')
    @Requires(property = 'spec.name', value = 'CustomStaticMappingLocalSpec')
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

        @Error(status = HttpStatus.BAD_REQUEST)
        HttpResponse badHandler() {
            HttpResponse.status(HttpStatus.BAD_REQUEST, "You sent me bad stuff - from Test1Controller.badHandler()")
        }

        @Error(status = HttpStatus.NOT_FOUND)
        HttpResponse notFoundHandler() {
            HttpResponse.status(HttpStatus.NOT_FOUND, "We cannot find anything - from Test1Controller.notFoundHandler()")
        }

        @Error(status = HttpStatus.UNSUPPORTED_MEDIA_TYPE, global = true)
        String unsupportedMediaTypeHandler() {
            "You sent an unsupported media type - from Test1Controller.unsupportedMediaTypeHandler()"
        }
    }

    @Controller('/test2')
    @Requires(property = 'spec.name', value = 'CustomStaticMappingLocalSpec')
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

        @Error(status = HttpStatus.BAD_REQUEST)
        HttpResponse badHandler() {
            HttpResponse.status(HttpStatus.BAD_REQUEST, "You sent me bad stuff - from Test2Controller.badHandler()")
        }
    }
}
