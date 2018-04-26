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
package io.micronaut.http.server.netty.status

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post

/**
 * Created by graemerocher on 13/09/2017.
 */
class CustomStaticMappingSpec extends AbstractMicronautSpec {

    void "test that a bad request response can be redirected by the router"() {
        when:
        rxClient.exchange('/test/bad').blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code
        e.response.reason() == "You sent me bad stuff - from TestController.badHandler()"

    }

    void "test that a bad request is handled is handled by the locally marked controller"() {
        when:
        rxClient.exchange('/test2/bad').blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code
        e.response.reason() == "You sent me bad stuff - from Test2Controller.badHandler()"

    }

    void "test that a bad request response for invalid request data can be redirected by the router"() {
        when:
        rxClient.exchange(
                HttpRequest.POST('/test/simple', [name:"Fred"])
                           .contentType(MediaType.FORM)
        ).blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.code() == HttpStatus.BAD_REQUEST.code
        e.response.reason() == "You sent me bad stuff - from TestController.badHandler()"

    }

    @Controller
    @Requires(property = 'spec.name', value = 'CustomStaticMappingSpec')
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED )
    static class TestController {
        @Get
        HttpResponse bad() {
            HttpResponse.badRequest()
        }

        @Post
        String simple(String name, Integer age) {
            "name: $name, age: $age"
        }

        @Error(status = HttpStatus.BAD_REQUEST)
        HttpResponse badHandler() {
            HttpResponse.status(HttpStatus.BAD_REQUEST, "You sent me bad stuff - from TestController.badHandler()")
        }
    }

    @Controller
    @Requires(property = 'spec.name', value = 'CustomStaticMappingSpec')
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED )
    static class Test2Controller {
        @Get
        HttpResponse bad() {
            HttpResponse.badRequest()
        }

        @Error(status = HttpStatus.BAD_REQUEST)
        HttpResponse badHandler() {
            HttpResponse.status(HttpStatus.BAD_REQUEST, "You sent me bad stuff - from Test2Controller.badHandler()")
        }
    }
}
