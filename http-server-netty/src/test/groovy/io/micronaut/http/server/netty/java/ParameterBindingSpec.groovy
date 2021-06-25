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
package io.micronaut.http.server.netty.java

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.netty.AbstractMicronautSpec
import reactor.core.publisher.Flux
import spock.lang.Unroll

import javax.annotation.Nullable

/**
 * Created by graemerocher on 25/08/2017.
 */
class ParameterBindingSpec extends AbstractMicronautSpec {

    void 'route is build correctly for @Controller("/books") method: @Get("{?max}")'() {
        given:
        HttpRequest request = HttpRequest.GET('/books')
        def response = rxClient.toBlocking().exchange(request)
        def status = response.status

        expect:
        status == HttpStatus.OK
    }

    void 'route is build correctly for @Controller("/poetry") method: @Get("/{?max}")'() {
        given:
        HttpRequest request = HttpRequest.GET('/poetry')
        def response = rxClient.toBlocking().exchange(request)
        def status = response.status

        expect:
        status == HttpStatus.OK
    }

    @Unroll
    void "test bind HTTP parameters for URI #uri"() {
        given:
        HttpResponse<String> response = Flux.from(rxClient.exchange(uri, String))
                .onErrorResume(t -> {
                    if (t instanceof HttpClientResponseException) {
                        return Flux.just(((HttpClientResponseException) t).response)
                    }
                    throw t
                }).blockFirst()
        HttpStatus status = response.status
        String body = null
        if (status == HttpStatus.OK) {
            body = response.body()
        }

        expect:
        body == result
        status == httpStatus

        where:
        uri                                                              | result                      | httpStatus
        '/java/parameter?max=20'                                         | "Parameter Value: 20"       | HttpStatus.OK
        '/java/parameter/simple?max=20'                                  | "Parameter Value: 20"       | HttpStatus.OK
        '/java/parameter/simple'                                         | null                        | HttpStatus.BAD_REQUEST
        '/java/parameter/named'                                          | null                        | HttpStatus.BAD_REQUEST
        '/java/parameter/named?maximum=20'                               | "Parameter Value: 20"       | HttpStatus.OK
        '/java/parameter/optional'                                       | "Parameter Value: 10"       | HttpStatus.OK
        '/java/parameter/optional?max=20'                                | "Parameter Value: 20"       | HttpStatus.OK
        '/java/parameter/nullable'                                       | "Parameter Value: null"     | HttpStatus.OK
        '/java/parameter/nullable?max=20'                                | "Parameter Value: 20"       | HttpStatus.OK
        HttpRequest.POST('/java/parameter/nullable-body', '{}')          | "Body Value: null"          | HttpStatus.OK
        HttpRequest.POST('/java/parameter/nullable-body', '{"max": 20}') | "Body Value: 20"            | HttpStatus.OK
        HttpRequest.POST('/java/parameter/requires-body', '{}')          | null                        | HttpStatus.BAD_REQUEST
        HttpRequest.POST('/java/parameter/requires-body', '{"max": 20}') | "Body Value: 20"            | HttpStatus.OK
        '/java/parameter/all'                                            | "Parameter Value: 10"       | HttpStatus.OK
        '/java/parameter/all?max=20'                                     | "Parameter Value: 20"       | HttpStatus.OK
        '/java/parameter/map?values.max=20&values.offset=30'             | "Parameter Value: 2030"     | HttpStatus.OK
        '/java/parameter/list?values=10,20'                              | "Parameter Value: [10, 20]" | HttpStatus.OK
        '/java/parameter/list?values=10&values=20'                       | "Parameter Value: [10, 20]" | HttpStatus.OK
        '/java/parameter/optional-list?values=10&values=20'              | "Parameter Value: [10, 20]" | HttpStatus.OK
    }

    @Requires(property = 'spec.name', value = 'ParameterBindingSpec')
    @Controller("/books")
    static class BookController {

        @Get("{?max}")
        HttpStatus index(@Nullable Integer max) {
            return HttpStatus.OK
        }
    }

    @Requires(property = 'spec.name', value = 'ParameterBindingSpec')
    @Controller("/poetry")
    static class PoetryController {

        @Get("/{?max}")
        HttpStatus index(@Nullable Integer max) {
            return HttpStatus.OK
        }
    }

}