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
package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Status
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HttpStatusSpec extends Specification {
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @AutoCleanup
    @Shared
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "Simple default return HttpStatus OK"() {
        when:
            Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                    HttpRequest.GET("/status/simple"), String
            ))
            HttpResponse<String> response = flowable.blockingFirst()
            Optional<String> body = response.getBody()

        then:
            response.status == HttpStatus.OK
            body.isPresent()
            body.get() == 'success'
    }

    void "Simple custom return HttpStatus CREATED"() {
        when:
            Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                    HttpRequest.GET("/status/simpleCreated"), String
            ))
            HttpResponse<String> response = flowable.blockingFirst()
            Optional<String> body = response.getBody()

        then:
            response.status == HttpStatus.CREATED
            body.isPresent()
            body.get() == 'success'
    }

    void "Simple custom return HttpStatus 404"() {
        when:
            Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                    HttpRequest.GET("/status/simple404"), String
            ))
            HttpResponse<String> response = flowable.blockingFirst()

        then:
            def e = thrown(HttpClientResponseException)
            e.message == "success"
            e.status == HttpStatus.NOT_FOUND
    }

    @Controller("/status")
    static class StatusController {

        @Get(uri = "/simple", produces = MediaType.TEXT_PLAIN)
        String simple() {
            return "success"
        }

        @Status(HttpStatus.CREATED)
        @Get(uri = "/simpleCreated", produces = MediaType.TEXT_PLAIN)
        String simpleCreated() {
            return "success"
        }

        @Status(HttpStatus.NOT_FOUND)
        @Get(uri = "/simple404", produces = MediaType.TEXT_PLAIN)
        String simple404() {
            return "success"
        }
    }
}