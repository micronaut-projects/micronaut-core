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
package io.micronaut.discovery.eureka

import io.micronaut.context.env.Environment
import io.micronaut.core.naming.NameUtils
import io.micronaut.discovery.DiscoveryClient
import io.reactivex.Flowable
import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.eureka.client.v2.EurekaClient
import io.micronaut.runtime.server.EmbeddedServer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.TimeUnit

/**
 * @author graemerocher
 * @since 1.0
 */
@IgnoreIf({System.getenv("TRAVIS")})
class EurekaAutoRegistrationSpec extends Specification{

    @Shared
    @AutoCleanup
    GenericContainer eurekaContainer =
            new GenericContainer("cloudready/spring-cloud-eureka-server:1.0.1")
                    .withExposedPorts(8761)
                    .waitingFor(new LogMessageWaitStrategy().withRegEx("(?s).*Started Eureka.*"))

    @Shared String eurekaHost
    @Shared int eurekaPort
    @Shared
    Map<String, Object> embeddedServerConfig



    def setupSpec() {
        eurekaContainer.start()
        eurekaHost = eurekaContainer.containerIpAddress
        eurekaPort = eurekaContainer.getMappedPort(8761)
        embeddedServerConfig = [
                (EurekaConfiguration.HOST): eurekaHost,
                (EurekaConfiguration.PORT): eurekaPort,
                "micronaut.caches.discoveryClient.enabled": false,
                'eureka.client.readTimeout': '5s'
        ] as Map<String, Object>
1    }

    void "test that an application can be registered and de-registered with Eureka"() {
        when: "An application is started and eureka configured"
        String serviceId = 'myService'
        def eurekaConfiguration = [
                'eureka.client.host'                       : eurekaHost,
                'eureka.client.port'                       : eurekaPort
        ]
        // run an application
        EmbeddedServer application = ApplicationContext.run(
                EmbeddedServer,
                ['consul.client.enabled': false,
                 'micronaut.application.name'                : serviceId] + eurekaConfiguration
        )

        // run a Eureka client
        EurekaClient eurekaClient = ApplicationContext.build(eurekaConfiguration).run(EurekaClient)

        // since Eureka is eventually consistent a long timeout/delay is required slowing this test down significantly
        PollingConditions conditions = new PollingConditions(timeout: 60, delay: 1)

        then: "The application is registered"
        conditions.eventually {
            Flowable.fromPublisher(eurekaClient.getInstances(serviceId)).timeout(60, TimeUnit.SECONDS).blockingFirst().size() == 1
            // Eureka uses upper case for application names
            Flowable.fromPublisher(eurekaClient.getServiceIds()).timeout(60, TimeUnit.SECONDS).blockingFirst().contains(
                    NameUtils.hyphenate(serviceId).toUpperCase()
            )
        }

        cleanup: "The application is stopped"
        application?.stop()
    }
}
