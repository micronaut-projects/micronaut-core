/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.docs.datavalidation.groups

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class EmailControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ['spec.name': 'datavalidationgroups'],
            "test")

    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    //tag::pojovalidateddefault[]
    def "invoking /email/createDraft parse parameters in a POJO and validates using default validation groups"() {
        when:
        Email email = new Email(subject: '', recipient: '')
        client.toBlocking().exchange(HttpRequest.POST('/email/createDraft', email))

        then:
        def e = thrown(HttpClientResponseException)
        def response = e.response
        response.status == HttpStatus.BAD_REQUEST

        when:
        email = new Email(subject: 'Hi', recipient: '')
        response = client.toBlocking().exchange(HttpRequest.POST('/email/createDraft', email))

        then:
        response.status == HttpStatus.OK
    }
    //end::pojovalidateddefault[]

    //tag::pojovalidatedfinal[]
    def "invoking /email/send parse parameters in a POJO and validates using FinalValidation validation group"() {
        when:
        Email email = new Email(subject: 'Hi', recipient: '')
        client.toBlocking().exchange(HttpRequest.POST('/email/send', email))

        then:
        def e = thrown(HttpClientResponseException)
        def response = e.response
        response.status == HttpStatus.BAD_REQUEST

        when:
        email = new Email(subject: 'Hi', recipient: 'me@micronaut.example')
        response = client.toBlocking().exchange(HttpRequest.POST('/email/send', email))

        then:
        response.status == HttpStatus.OK
    }
    //end::pojovalidatedfinal[]
}
