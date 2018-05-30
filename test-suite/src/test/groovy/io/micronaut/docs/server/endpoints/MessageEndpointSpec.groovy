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

package io.micronaut.docs.server.endpoints

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class MessageEndpointSpec extends Specification {

    void "test read message endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                ['endpoints.message.enabled': true])
        RxHttpClient rxClient = server.applicationContext.createBean(RxHttpClient, server.getURL())

        when:
        def response = rxClient.exchange("/message", String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == "default message"

        cleanup:
        server.close()
    }

    void "test write message endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                ['endpoints.message.enabled': true])
        RxHttpClient rxClient = server.applicationContext.createBean(RxHttpClient, server.getURL())

        when:
        def response = rxClient.exchange(HttpRequest.POST("/message", [newMessage: "A new message"]), String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == "Message updated"

        when:
        response = rxClient.exchange("/message", String).blockingFirst()

        then:
        response.body() == "A new message"

        cleanup:
        server.close()
    }

    void "test delete message endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                ['endpoints.message.enabled': true])
        RxHttpClient rxClient = server.applicationContext.createBean(RxHttpClient, server.getURL())

        when:
        def response = rxClient.exchange(HttpRequest.DELETE("/message"), String).blockingFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == "Message deleted"

        when:
        rxClient.exchange("/message", String).blockingFirst()

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status.code == 404

        cleanup:
        server.close()
    }
}
