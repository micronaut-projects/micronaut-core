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
package io.micronaut.discovery.eureka

import io.reactivex.Flowable
import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.eureka.client.v2.EurekaClient
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * @author graemerocher
 * @since 1.0
 */
class EurekaMockBasicAuthSpec extends Specification {

    void "test authentication works"() {
        given: "a mock server with auth enabled"
        EmbeddedServer eurekaServer = ApplicationContext.run(EmbeddedServer, [
                'jackson.serialization.WRAP_ROOT_VALUE': true,
                'test.eureka.userinfo'                 : 'foo:bar',
                (MockEurekaServer.ENABLED): true
        ])

        and: "A client with the token"
        def serviceName = 'authenticated-server'
        EmbeddedServer anotherServer = ApplicationContext.run(EmbeddedServer, ['micronaut.application.name'  : serviceName,
                                                                               'consul.client.registration.enabled': false,
                                                                               'jackson.deserialization.UNWRAP_ROOT_VALUE': true,
                                                                               'eureka.client.defaultZone'  : "http://foo:bar@localhost:${eurekaServer.port}"])

        EurekaClient eurekaClient = anotherServer.applicationContext.getBean(EurekaClient)

        and: "Another client without the token"
        HttpClient unauthorizedClient = anotherServer.getApplicationContext().createBean(HttpClient, eurekaServer.getURL())

        when:
        PollingConditions conditions = new PollingConditions(timeout: 5)

        then:"the authorized app is registered"
        conditions.eventually {
            Flowable.fromPublisher(eurekaClient.getServiceIds()).blockingFirst().contains(serviceName)
        }

        when:"The unauthorized client is used"
        unauthorizedClient.toBlocking().retrieve('/eureka/apps')

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.FORBIDDEN

        cleanup:
        anotherServer?.stop()
        eurekaServer?.stop()
    }
}
