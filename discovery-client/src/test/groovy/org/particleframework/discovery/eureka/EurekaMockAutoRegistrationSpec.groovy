/*
 * Copyright 2018 original authors
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
package org.particleframework.discovery.eureka

import io.reactivex.Flowable
import org.particleframework.context.ApplicationContext
import org.particleframework.discovery.DiscoveryClient
import org.particleframework.discovery.eureka.client.v2.EurekaClient
import org.particleframework.discovery.eureka.client.v2.InstanceInfo
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import javax.validation.ConstraintViolationException

/**
 * @author graemerocher
 * @since 1.0
 */
class EurekaMockAutoRegistrationSpec extends Specification {


    void "test that an application can be registered and de-registered with Eureka"() {

        given:
        EmbeddedServer eurekaServer = ApplicationContext.run(EmbeddedServer, [
                'jackson.serialization.WRAP_ROOT_VALUE': true
        ])

        when: "An application is started and eureka configured"
        String serviceId = 'myService'
        EmbeddedServer application = ApplicationContext.run(
                EmbeddedServer,
                ['consul.registration.enabled'              : false,
                 'eureka.client.host'                       : eurekaServer.getHost(),
                 'eureka.client.port'                       : eurekaServer.getPort(),
                 'jackson.deserialization.UNWRAP_ROOT_VALUE': true,
                 'particle.application.name'                : serviceId]
        )

        EurekaClient eurekaClient = application.applicationContext.getBean(EurekaClient)
        PollingConditions conditions = new PollingConditions(timeout: 5)

        then: "The application is registered"
        conditions.eventually {
            Flowable.fromPublisher(eurekaClient.getInstances(serviceId)).blockingFirst().size() == 1
            Flowable.fromPublisher(eurekaClient.getServiceIds()).blockingFirst().contains(serviceId)
            MockEurekaServer.instances[serviceId].size() == 1

            InstanceInfo instanceInfo = MockEurekaServer.instances[serviceId].values().first()
            instanceInfo.status == InstanceInfo.Status.UP
        }



        when: "The application is stopped"
        application?.stop()

        then: "The application is de-registered"
        conditions.eventually {
            MockEurekaServer.instances[serviceId].size() == 0
        }

        when:"test validation"
        eurekaClient.register("", null)

        then:"Invalid arguments thrown"
        thrown(ConstraintViolationException)

        cleanup:
        eurekaServer?.stop()
    }


    @Unroll
    void "test that an application can be registered and de-registered with Eureka with metadata"() {

        given:
        EmbeddedServer eurekaServer = ApplicationContext.run(EmbeddedServer, [
                'jackson.serialization.WRAP_ROOT_VALUE': true
        ])

        def map = ['consul.registration.enabled'              : false,
                   'eureka.client.host'                       : eurekaServer.getHost(),
                   'eureka.client.port'                       : eurekaServer.getPort(),
                   'jackson.deserialization.UNWRAP_ROOT_VALUE': true,
                   'particle.application.name'                : serviceId]

        for(entry in configuration) {
            map.put("eureka.client.registration.$entry.key".toString(), entry.value)
        }

        EmbeddedServer application = ApplicationContext.run(
                EmbeddedServer,
                map
        )

        DiscoveryClient discoveryClient = application.applicationContext.getBean(EurekaClient)
        PollingConditions conditions = new PollingConditions(timeout: 5)

        expect: "The metadata is correct"
        conditions.eventually {
            Flowable.fromPublisher(discoveryClient.getInstances(serviceId)).blockingFirst().size() == 1
            MockEurekaServer.instances[serviceId].size() == 1

            InstanceInfo instanceInfo = MockEurekaServer.instances[serviceId].values().first()
            configuration.every {
                instanceInfo."$it.key" == it.value
            }
        }

        cleanup:
        application?.stop()
        eurekaServer?.stop()

        where:
        serviceId   | configuration
        'myService' | ['asgName':'test', vipAddress:'myVip', secureVipAddress:'mySecureVip', appGroupName:'myAppGroup', status:InstanceInfo.Status.STARTING]
        'myService' | [homePageUrl:'http://home', statusPageUrl:'http://status', healthCheckUrl:'http://health', secureHealthCheckUrl:'http://securehealth']
        'myService' | ['metadata':[foo:'bar']]
        'myService' | [port:9999, securePort:9998]

    }
}
