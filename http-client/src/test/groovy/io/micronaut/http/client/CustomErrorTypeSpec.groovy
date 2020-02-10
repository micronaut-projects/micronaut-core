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


import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class CustomErrorTypeSpec extends Specification {

    @Inject
    CustomErrorClient customErrorClient

    @Inject
    @Client("/")
    RxHttpClient client

    void "test custom error type"() {

        when:
        customErrorClient.index()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.getBody(MyError).get().reason == 'bad things'
    }

    void "test custom error type with generic"() {
        Argument<OtherError> errorType = Argument.of(OtherError, String)

        when:
        client.toBlocking().exchange(HttpRequest.GET("/test/custom-errors/other"), Argument.of(String), errorType)

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(errorType).get().reason == 'bad things'
    }

    @Controller('/test/custom-errors')
    static class CustomErrorController {

        @Get("/")
        HttpResponse index() {
            HttpResponse.serverError().body(new MyError(reason: "bad things"))
        }

        @Get("/other")
        HttpResponse index2() {
            HttpResponse.serverError().body(new OtherError(reason: "bad things"))
        }
    }

    @Client(value = '/test/custom-errors', errorType = MyError)
    static interface CustomErrorClient {
        @Get("/")
        HttpResponse index()
    }

    static class MyError {
        String reason
    }

    static class OtherError<T> {
        T reason
    }
}
