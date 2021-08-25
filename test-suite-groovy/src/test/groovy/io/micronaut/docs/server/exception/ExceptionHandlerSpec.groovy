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
package io.micronaut.docs.server.exception

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ExceptionHandlerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': ExceptionHandlerSpec.simpleName
    ], Environment.TEST)

    @AutoCleanup
    @Shared
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "test OutOfStockException is handled by ExceptionHandler"() {
        when:
        Argument<Map<String, Object>> errorType = Argument.mapOf(String.class, Object.class);
        HttpRequest request = HttpRequest.GET('/books/stock/1234')
        client.toBlocking().retrieve(request, Argument.LONG, errorType)

        then:
        def ex = thrown(HttpClientResponseException)
        HttpResponse response = ex.getResponse()
        Map<String, Object> body = (Map<String, Object>) response.getBody(errorType).get()
        response.status() == HttpStatus.BAD_REQUEST
        body._embedded.errors[0].message == "No stock available"
    }
}
