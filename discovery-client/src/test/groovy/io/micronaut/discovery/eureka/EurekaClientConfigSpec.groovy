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
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.eureka.client.v2.EurekaClient
import io.micronaut.runtime.ApplicationConfiguration
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class EurekaClientConfigSpec extends Specification {
    void 'test configure default zone'() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'eureka.client.defaultZone': value
        )
        EurekaConfiguration config = applicationContext.getBean(EurekaConfiguration)
        List<ServiceInstance> serviceInstances = config.allZones

        expect:
        serviceInstances == result

        where:
        value                           | result
        'localhost:8087'                | [newServiceInstance("http://$value")]
        'localhost:8087,localhost:8088' | [newServiceInstance("http://localhost:8087"), newServiceInstance("http://localhost:8088")]
    }

    void 'test configure other zones'() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'eureka.client.zones.test': value
        )
        EurekaConfiguration config = applicationContext.getBean(EurekaConfiguration)
        List<ServiceInstance> serviceInstances = config.allZones

        expect:
        serviceInstances == result
        serviceInstances.every() { it.zone.isPresent() && it.zone.get() == 'test'}

        where:
        value                           | result
        'localhost:8087'                | [newServiceInstance("http://$value")]
        'localhost:8087,localhost:8088' | [newServiceInstance("http://localhost:8087"), newServiceInstance("http://localhost:8088")]
    }

    private ServiceInstance newServiceInstance(String url) {
        ServiceInstance.builder(EurekaClient.SERVICE_ID, new URI(url)).build()
    }

    void "test configure registration client"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'eureka.client.registration.enabled': false,
                'eureka.client.discovery.enabled': false,
                (ApplicationConfiguration.APPLICATION_NAME): 'foo',
                (ApplicationConfiguration.InstanceConfiguration.INSTANCE_ID): 'foo-1',
                'eureka.client.registration.asgName': 'myAsg',
                'eureka.client.registration.countryId': '10',
                'eureka.client.registration.ipAddr': '10.10.10.10',
                'eureka.client.registration.vipAddress': 'something',
                'eureka.client.registration.leaseInfo.durationInSecs': '60',
                'eureka.client.registration.metadata.foo': 'bar'
        )
        EurekaConfiguration config = applicationContext.getBean(EurekaConfiguration)


        expect:
        !config.discovery.enabled
        !config.registration.enabled
        config.registration.instanceInfo.asgName == 'myAsg'
        config.registration.instanceInfo.app == 'foo'
        config.registration.instanceInfo.id == 'foo-1'
        config.registration.instanceInfo.ipAddr == '10.10.10.10'
        config.registration.instanceInfo.countryId == 10
        config.registration.instanceInfo.vipAddress == 'something'
        config.registration.instanceInfo.leaseInfo.durationInSecs == 60
        config.registration.instanceInfo.metadata == [foo: 'bar']
    }
}
