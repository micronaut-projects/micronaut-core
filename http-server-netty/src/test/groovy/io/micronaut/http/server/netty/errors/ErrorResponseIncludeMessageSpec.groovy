/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.errors

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class ErrorResponseIncludeMessageSpec extends Specification {

    void "default error response suppresses exception messages"() {
        expect:
        errorMessage([:]) == 'Internal Server Error'
    }

    void "always includes exception messages"() {
        expect:
        errorMessage(['micronaut.server.error-response-include-message': 'always']) == 'Internal Server Error: Sensitive message'
    }

    void "on-param includes exception messages only when requested"() {
        given:
        Map<String, Object> configuration = ['micronaut.server.error-response-include-message': 'on-param']

        expect:
        errorMessage(configuration) == 'Internal Server Error'
        errorMessage(configuration, '/error-response-include-message?message') == 'Internal Server Error: Sensitive message'
        errorMessage(configuration, '/error-response-include-message?message=false') == 'Internal Server Error'
    }

    private static String errorMessage(Map<String, Object> configuration, String path = '/error-response-include-message') {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, configuration)
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)
        try {
            client.toBlocking().exchange(HttpRequest.GET(path), Map)
            throw new AssertionError('Expected an HTTP 500 response')
        } catch (HttpClientResponseException e) {
            assert e.status == HttpStatus.INTERNAL_SERVER_ERROR
            return e.response.getBody(Map).get()._embedded.errors[0].message
        } finally {
            client.close()
            server.close()
        }
    }

    @Controller
    static class ErrorResponseIncludeMessageController {
        @Get("/error-response-include-message")
        String error() {
            throw new IllegalStateException('Sensitive message')
        }
    }
}
