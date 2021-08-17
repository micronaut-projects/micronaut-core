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

import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.hateoas.JsonError
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

@Property(name = 'spec.name', value = 'ServerErrorSpec')
@MicronautTest
class ServerErrorSpec extends Specification {

    @Inject
    MyClient myClient

    void "test 500 error"() {
        when:
        myClient.clientNonReactiveControllerWithServerError()

        then:
        HttpClientResponseException e = thrown()
        e.message == "Bad things happening"
    }

    void "test 500 error - single"() {
        when:
        Mono.from(myClient.clientSingleResultControllerWithServerError()).block()

        then:
        HttpClientResponseException e = thrown()
        e.message == "Bad things happening"
    }

    void "test exception error"() {
        when:
        myClient.clientNonReactiveControllerError()

        then:
        HttpClientResponseException e = thrown()
        e.response.getBody(Map).get()._embedded.errors[0].message == "Internal Server Error: Bad things happening"
    }

    void "test exception error - mono"() {
        when:
        Mono.from(myClient.clientSingleResultControllerError()).block()

        then:
        HttpClientResponseException e = thrown()
        e.response.getBody(Map).get()._embedded.errors[0].message == "Internal Server Error: Bad things happening"
    }

    void "test single error"() {
        when:
        myClient.clientNonReactiveControllerSingleResultError()

        then:
        HttpClientResponseException e = thrown()
        e.response.getBody(Map).get()._embedded.errors[0].message == "Internal Server Error: Bad things happening"
    }

    void "test single error - single"() {
        when:
        Mono.from(myClient.clientSingleResultControllerSingleResultError()).block()

        then:
        HttpClientResponseException e = thrown()
        e.response.getBody(Map).get()._embedded.errors[0].message == "Internal Server Error: Bad things happening"
    }

    void "test flowable error - flowable"() {
        when:
        HttpResponse<?> response = Flux.from(myClient.clientReactiveSequenceControllerReactiveSequenceError())
                .onErrorResume(throwable -> {
            if (throwable instanceof HttpClientResponseException) {
                return Flux.just(HttpResponse.status(((HttpClientResponseException) throwable).status).body(throwable.message))
            }
            throw throwable
        }).blockFirst()

        then:
        response.body.isPresent()
        response.body.get() == "Internal Server Error"
    }

    @Requires(property = 'spec.name', value = 'ServerErrorSpec')
    @Client('/server-errors')
    static interface MyClient {
        @Get('/five-hundred')
        HttpResponse clientNonReactiveControllerWithServerError()

        @Get('/five-hundred')
        @SingleResult
        Publisher clientSingleResultControllerWithServerError()

        @Get('/exception')
        HttpResponse clientNonReactiveControllerError()

        @Get('/exception')
        @SingleResult
        Publisher clientSingleResultControllerError()

        @Get('/single-result-error')
        HttpResponse clientNonReactiveControllerSingleResultError()

        @Get('/single-result-error')
        @SingleResult
        Publisher clientSingleResultControllerSingleResultError()

        @Get('/flowable-error')
        Publisher clientReactiveSequenceControllerReactiveSequenceError()
    }

    @Requires(property = 'spec.name', value = 'ServerErrorSpec')
    @Controller('/server-errors')
    static class ServerErrorController {

        @Get('/five-hundred')
        HttpResponse fiveHundred() {
            HttpResponse.serverError()
                        .body(new JsonError("Bad things happening"))
        }

        @Get('/exception')
        HttpResponse error() {
            throw new RuntimeException("Bad things happening")
        }

        @Get('/single-result-error')
        @SingleResult
        Publisher singleResultError() {
            Mono.error(new RuntimeException("Bad things happening"))
        }

        @Get('/flowable-error')
        Publisher reactiveSequenceError() {
            Flux.error(new RuntimeException("Bad things happening"))
        }
    }
}
