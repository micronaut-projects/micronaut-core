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
import spock.util.concurrent.PollingConditions

/**
 * @author graemerocher
 * @since 1.0
 */
class EurekaMockHeartbeatSpec extends Specification {


    void "test that the server reports a heartbeat to Eureka"() {

        given:
        EmbeddedServer eurekaServer = ApplicationContext.run(EmbeddedServer, [
                'jackson.serialization.WRAP_ROOT_VALUE': true,
                (MockEurekaServer.ENABLED): true
        ])

        when: "An application is started and eureka configured"
        String serviceId = 'heartbeatService'
        EmbeddedServer application = ApplicationContext.run(
                EmbeddedServer,
                ['consul.client.registration.enabled'       : false,
                 'eureka.client.host'                       : eurekaServer.getHost(),
                 'eureka.client.port'                       : eurekaServer.getPort(),
                 'jackson.deserialization.UNWRAP_ROOT_VALUE': true,
                 'particle.application.name'                : serviceId,
                 'particle.heartbeat.interval'              : '1s']
        )

        DiscoveryClient discoveryClient = application.applicationContext.getBean(EurekaClient)
        PollingConditions conditions = new PollingConditions(timeout: 5)

        then: "The application is registered"
        conditions.eventually {
            Flowable.fromPublisher(discoveryClient.getInstances(serviceId)).blockingFirst().size() == 1
            MockEurekaServer.instances[serviceId].size() == 1

            InstanceInfo instanceInfo = MockEurekaServer.instances[serviceId].values().first()
            instanceInfo.status == InstanceInfo.Status.UP
            // heart beat received
            MockEurekaServer.heartbeats[serviceId].values().first()
        }

        when: "The application is stopped"
        application?.stop()

        then: "The application is de-registered"
        conditions.eventually {
            MockEurekaServer.instances[serviceId].size() == 0
        }

        cleanup:
        eurekaServer?.stop()
    }

}
