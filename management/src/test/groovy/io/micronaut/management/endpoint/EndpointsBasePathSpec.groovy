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
package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class EndpointsBasePathSpec extends Specification {

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            [
                    'spec.name': getClass().simpleName,
                    'endpoints.all.path': '/admin',
                    'endpoints.all.enabled': true
            ], Environment.TEST)

    @AutoCleanup
    @Shared
    RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    def "due to the change of endpoints base path to /admin, Health endpoint is available at /admin/health"() {
        when:
        rxClient.toBlocking().retrieve('/admin/health')

        then:
        noExceptionThrown()
    }

    def "due to the change of endpoints base path to /admin, Health endpoint is not available at /health"() {
        when:
        rxClient.toBlocking().retrieve('/health')

        then:
        def e = thrown(HttpClientResponseException)

        when:
        def response = e.response

        then:
        response.status == HttpStatus.NOT_FOUND
    }
}
