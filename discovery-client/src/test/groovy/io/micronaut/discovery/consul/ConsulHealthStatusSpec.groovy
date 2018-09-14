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
package io.micronaut.discovery.consul

import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.reactivex.Flowable
import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.health.HealthStatus
import io.micronaut.http.HttpStatus
import io.micronaut.runtime.server.EmbeddedServer
import org.testcontainers.containers.GenericContainer
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * @author graemerocher
 * @since 1.0
 */
class ConsulHealthStatusSpec extends Specification {

    @Shared
    @AutoCleanup
    GenericContainer consulContainer =
            new GenericContainer("consul:latest")
                    .withExposedPorts(8500)

    @Shared String consulHost
    @Shared int consulPort

    def setupSpec() {
        consulContainer.start()
        consulHost = consulContainer.containerIpAddress
        consulPort = consulContainer.getMappedPort(8500)
    }

    void "test the consul service's health status is correct"() {
        given:

        String serviceId = 'test-auto-reg'

        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(
                EmbeddedServer,
                ['consul.client.host': consulHost,
                 'consul.client.port': consulPort,
                 'micronaut.application.name': serviceId] // short heart beat interval
        )

        Map discoveryClientMap = ['consul.client.host': consulHost,
                                  'consul.client.port': consulPort,
                                  "micronaut.caches.discovery-client.enabled": false]
        DiscoveryClient discoveryClient = ApplicationContext.run(DiscoveryClient, discoveryClientMap)

        PollingConditions conditions = new PollingConditions(timeout: 3)

        then:
        conditions.eventually {
            List<ServiceInstance> instances = Flowable.fromPublisher(discoveryClient.getInstances(serviceId)).blockingFirst()
            instances.size() == 1
            instances[0].port == embeddedServer.getPort()
            instances[0].host == embeddedServer.getHost()
        }

        when:"An application is set to fail"
        ConsulClient consulClient = embeddedServer.getApplicationContext().getBean(ConsulClient)
        HttpStatus status = Flowable.fromPublisher(consulClient.fail("service:$serviceId:${embeddedServer.port}")).blockingFirst()

        then:"The status is ok"
        status == HttpStatus.OK

        when:"The service is retrieved"
        def services = Flowable.fromPublisher(consulClient.getInstances(serviceId)).blockingFirst()

        then:"The service is down"
        services.size() == 1
        services[0].healthStatus == HealthStatus.DOWN

        cleanup:
        embeddedServer?.stop()
    }
}
