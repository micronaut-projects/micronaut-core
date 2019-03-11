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
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CustomErrorTypeSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    void "test custom error type"() {

        given:
        CustomErrorClient client = embeddedServer.getApplicationContext().getBean(CustomErrorClient)

        when:
        client.index()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.getBody(MyError).get().reason == 'bad things'

    }

    @Controller('/test/custom-errors')
    static class CustomErrorController {

        @Get("/")
        HttpResponse index() {
            HttpResponse.serverError().body(new MyError(reason: "bad things"))
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
}
