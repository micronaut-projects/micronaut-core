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

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.hateoas.JsonError
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author Fabien Renaud
 * @since 1.3.0
 */
class ExceptionOnErrorStatusSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            "micronaut.http.client.exceptionOnErrorStatus":'false'
    )

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

    void "test not found"() {
        when:
        def res = client.toBlocking().exchange(HttpRequest.GET('/return-response/doesnotexist'), Argument.STRING, Argument.STRING)

        then:
        res.status == HttpStatus.NOT_FOUND
        res.getBody(String).isPresent()
    }

    void "test internal server error"() {
        when:
        def res = client.toBlocking().exchange(HttpRequest.GET('/return-response/error'), Argument.STRING, Argument.STRING)

        then:
        res.status == HttpStatus.INTERNAL_SERVER_ERROR
        res.getBody(String).get() == "Server error"
    }

    @Controller("/return-response")
    static class GetController {

        @Get(value = "/error", produces = MediaType.TEXT_PLAIN)
        HttpResponse error() {
            return HttpResponse.serverError().body("Server error")
        }
    }
}
