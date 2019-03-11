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
package io.micronaut.http.client.docs.httpclientexceptionbody

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class BindHttpClientExceptionBodySpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': BindHttpClientExceptionBodySpec.simpleName], Environment.TEST)

    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.getApplicationContext().createBean(HttpClient.class, embeddedServer.getURL())

    //tag::test[]
    def "after an HttpClientException the response body can be bound to a POJO"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("/books/1680502395"),
                Argument.of(Book), // <1>
                Argument.of(CustomError)) // <2>

        then:
        def e = thrown(HttpClientException)
        e.response.status == HttpStatus.UNAUTHORIZED

        when:
        Optional<CustomError> jsonError = e.response.getBody(CustomError)

        then:
        jsonError.isPresent()
        jsonError.get().status == 401
        jsonError.get().error == 'Unauthorized'
        jsonError.get().message == 'No message available'
        jsonError.get().path == '/books/1680502395'
    }
    //end::test[]

    def "verify ok bound"() {
        when:
        HttpResponse rsp = client.toBlocking().exchange(HttpRequest.GET("/books/1491950358"),
                Argument.of(Book),
                Argument.of(CustomError))

        then:
        noExceptionThrown()
        rsp.status == HttpStatus.OK
    }
}
