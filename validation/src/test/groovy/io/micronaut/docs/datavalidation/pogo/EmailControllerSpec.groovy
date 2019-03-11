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
package io.micronaut.docs.datavalidation.pogo

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class EmailControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ['spec.name': 'datavalidationpogo'],
            "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient, embeddedServer.getURL())

    //tag::pojovalidated[]
    def "invoking /email/send parse parameters in a POJO and validates"() {
        when:
        Email email = new Email()
        email.subject = 'Hi'
        email.recipient = ''
        client.toBlocking().exchange(HttpRequest.POST('/email/send', email))

        then:
        def e = thrown(HttpClientResponseException)

        when:
        def response = e.response

        then:
        response.status == HttpStatus.BAD_REQUEST

        when:
        email = new Email()
        email.subject = 'Hi'
        email.recipient = 'me@micronaut.example'
        client.toBlocking().exchange(HttpRequest.POST('/email/send', email))

        then:
        noExceptionThrown()
    }
    //end::pojovalidated[]
}
