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


import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.hateoas.JsonError
import io.micronaut.test.annotation.MicronautTest
import io.reactivex.Single
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class ServerErrorSpec extends Specification {
    @Inject
    MyClient myClient

    void "test 500 error"() {
        when:
        myClient.fiveHundred()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Bad things happening"
    }

    void "test 500 error - single"() {
        when:
        myClient.fiveHundredSingle().blockingGet()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Bad things happening"
    }

    void "test exception error"() {
        when:
        myClient.exception()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error: Bad things happening"
    }

    void "test exception error - single"() {
        when:
        myClient.exceptionSingle().blockingGet()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error: Bad things happening"
    }

    void "test single error"() {
        when:
        myClient.singleError()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error: Bad things happening"
    }

    void "test single error - single"() {
        when:
        myClient.singleErrorSingle().blockingGet()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error: Bad things happening"
    }

    @Client('/server-errors')
    static interface MyClient {
        @Get('/five-hundred')
        HttpResponse fiveHundred()

        @Get('/five-hundred')
        Single fiveHundredSingle()

        @Get('/exception')
        HttpResponse exception()

        @Get('/exception')
        Single exceptionSingle()

        @Get('/single-error')
        HttpResponse singleError()

        @Get('/single-error')
        Single singleErrorSingle()
    }

    @Controller('/server-errors')
    static class ServerErrorController {

        @Get('/five-hundred')
        HttpResponse fiveHundred() {
            HttpResponse.serverError()
                        .body(new JsonError("Bad things happening"))
        }

        @Get('/exception')
        HttpResponse exception() {
            throw new RuntimeException("Bad things happening")
        }

        @Get('/single-error')
        Single singleError() {
            Single.error(new RuntimeException("Bad things happening"))
        }
    }
}
