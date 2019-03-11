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
import io.reactivex.Flowable
import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.CompositeDiscoveryClient
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.eureka.client.v2.ApplicationInfo
import io.micronaut.discovery.eureka.client.v2.EurekaClient
import io.micronaut.discovery.eureka.client.v2.InstanceInfo
import io.micronaut.http.HttpStatus
import io.micronaut.runtime.server.EmbeddedServer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import spock.lang.Retry
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Shared
import spock.lang.AutoCleanup
import spock.util.concurrent.PollingConditions

import javax.validation.ConstraintViolationException

/**
 * @author graemerocher
 * @since 1.0
 */
@Stepwise
class EurekaClientSpec extends Specification {

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

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer

    @Shared EurekaClient client
    @Shared DiscoveryClient discoveryClient


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
        embeddedServer = ApplicationContext.run(EmbeddedServer, embeddedServerConfig, Environment.TEST)
        client = embeddedServer.applicationContext.getBean(EurekaClient)
        discoveryClient = embeddedServer.applicationContext.getBean(DiscoveryClient)
    }

    void "test is a discovery client"() {
        expect:
        discoveryClient instanceof CompositeDiscoveryClient
        client instanceof DiscoveryClient
        embeddedServer.applicationContext.getBean(EurekaConfiguration).readTimeout.isPresent()
        embeddedServer.applicationContext.getBean(EurekaConfiguration).readTimeout.get().getSeconds() == 5
    }
    
    void "test validation"() {
        when:
        client.register("", null)
        
        then:
        thrown(ConstraintViolationException)

        when:
        client.register("ok", null)

        then:
        thrown(ConstraintViolationException)

    }

    @Retry
    void "test register and de-register instance"() {

        given:
        PollingConditions conditions = new PollingConditions(timeout: 25, delay: 1)

        when:
        def instanceId = "myapp-1"
        def appId = "myapp"
        HttpStatus status = Flowable.fromPublisher(client.register(appId, new InstanceInfo("localhost", appId, instanceId))).blockingFirst()

        then:
        status == HttpStatus.NO_CONTENT

        // NOTE: Eureka is eventually consistent so this sometimes fails due to the timeout in PollingConditions not being met
        conditions.eventually {

            ApplicationInfo applicationInfo = Flowable.fromPublisher(client.getApplicationInfo(appId)).blockingFirst()

            InstanceInfo instanceInfo = Flowable.fromPublisher(client.getInstanceInfo(appId, instanceId)).blockingFirst()

            applicationInfo.name == appId.toUpperCase()
            applicationInfo.instances.size() == 1
            instanceId != null
            instanceInfo.id == instanceId
            instanceInfo.app == applicationInfo.name
        }

        when:
        status = Flowable.fromPublisher(client.deregister(appId, instanceId)).blockingFirst()

        then:
        status == HttpStatus.OK
    }
}
