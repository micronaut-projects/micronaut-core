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

import io.micronaut.context.ApplicationContext
import io.micronaut.core.naming.NameUtils
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.eureka.client.v2.EurekaClient
import io.micronaut.discovery.eureka.client.v2.InstanceInfo
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import javax.validation.ConstraintViolationException

/**
 * @author graemerocher
 * @since 1.0
 */
@Stepwise
class EurekaMockAutoRegistrationSpec extends Specification {


    void "test that an application can be registered and de-registered with Eureka"() {

        given:
        EmbeddedServer eurekaServer = ApplicationContext.run(EmbeddedServer, [
                'jackson.serialization.WRAP_ROOT_VALUE': true,
                (MockEurekaServer.ENABLED): true
        ])

        when: "An application is started and eureka configured"
        String serviceId = 'myService'
        EmbeddedServer application = ApplicationContext.run(
                EmbeddedServer,
                ['consul.client.registration.enabled'              : false,
                 "micronaut.caches.discoveryClient.enabled": false,
                 'eureka.client.host'                       : eurekaServer.getHost(),
                 'eureka.client.port'                       : eurekaServer.getPort(),
                 'jackson.deserialization.UNWRAP_ROOT_VALUE': true,
                 'micronaut.application.name'                : serviceId]
        )

        EurekaClient eurekaClient = application.applicationContext.getBean(EurekaClient)
        PollingConditions conditions = new PollingConditions(timeout: 5, delay: 0.5)

        then: "The application is registered"
        conditions.eventually {
            Flowable.fromPublisher(eurekaClient.applicationInfos).blockingFirst().size() == 1
            Flowable.fromPublisher(eurekaClient.getApplicationVips(NameUtils.hyphenate(serviceId))).blockingFirst().size() == 1
            Flowable.fromPublisher(eurekaClient.getInstances(NameUtils.hyphenate(serviceId))).blockingFirst().size() == 1
            Flowable.fromPublisher(eurekaClient.getServiceIds()).blockingFirst().contains(NameUtils.hyphenate(serviceId))
            MockEurekaServer.instances[NameUtils.hyphenate(serviceId)].size() == 1

            InstanceInfo instanceInfo = MockEurekaServer.instances[NameUtils.hyphenate(serviceId)].values().first()
            instanceInfo.status == InstanceInfo.Status.UP
        }



        when: "The application is stopped"
        application?.stop()

        then: "The application is de-registered"
        conditions.eventually {
            MockEurekaServer.instances[NameUtils.hyphenate(serviceId)].size() == 0
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
                'jackson.serialization.WRAP_ROOT_VALUE': true,
                (MockEurekaServer.ENABLED): true
        ])

        def map = ['consul.registration.enabled'              : false,
                   'eureka.client.host'                       : eurekaServer.getHost(),
                   'eureka.client.port'                       : eurekaServer.getPort(),
                   'jackson.deserialization.UNWRAP_ROOT_VALUE': true,
                   'micronaut.application.name'                : serviceId]

        for(entry in configuration) {
            map.put("eureka.client.registration.$entry.key".toString(), entry.value)
        }

        EmbeddedServer application = ApplicationContext.run(
                EmbeddedServer,
                map
        )

        DiscoveryClient discoveryClient = application.applicationContext.getBean(EurekaClient)
        PollingConditions conditions = new PollingConditions(timeout: 5, delay: 0.5)

        expect: "The metadata is correct"
        conditions.eventually {
            Flowable.fromPublisher(discoveryClient.getInstances(serviceId)).blockingFirst().size() == 1
            MockEurekaServer.instances[NameUtils.hyphenate(serviceId)].size() == 1

            InstanceInfo instanceInfo = MockEurekaServer.instances[NameUtils.hyphenate(serviceId)].values().first()
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
