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
package io.micronaut.management.endpoint.stop

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * @author James Kleeh
 * @since 1.0
 */
class ServerStopEndpointSpec extends Specification {

    void "test the endpoint is disabled by default"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.stop.sensitive': false], Environment.TEST)
        HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        rxClient.exchange("/stop").blockFirst()

        then:
        HttpClientResponseException ex = thrown()
        ex.response.code() == HttpStatus.NOT_FOUND.code

        cleanup:
        rxClient.close()
        embeddedServer.close()
    }

    void "test the server is stopped after exercising the endpoint"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.stop.enabled': true, 'endpoints.stop.sensitive': false], Environment.TEST)
        HttpClient rxClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())
        def conditions = new PollingConditions(timeout: 10, initialDelay: 3, delay: 1, factor: 1)

        when:
        def response = rxClient.exchange(HttpRequest.POST("/stop", ""), String).blockFirst()

        then:
        response.code() == HttpStatus.OK.code
        response.body() == '{"message":"Server shutdown started"}'
        conditions.eventually {
            assert !embeddedServer.isRunning()
        }

        cleanup:
        rxClient.close()
        embeddedServer.close()
    }
}
