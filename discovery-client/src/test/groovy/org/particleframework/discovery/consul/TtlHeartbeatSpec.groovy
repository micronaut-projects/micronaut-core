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
package org.particleframework.discovery.consul

import io.reactivex.Flowable
import org.particleframework.context.ApplicationContext
import org.particleframework.discovery.DiscoveryClient
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * @author graemerocher
 * @since 1.0
 */
class TtlHeartbeatSpec extends Specification {


    void "test that the server reports a TTL heartbeat when configured to do so"() {

        given:
        EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer)

        when:"An application is started that sends a heart beat to consul"
        String serviceId = 'myService'
        EmbeddedServer application = ApplicationContext.run(
                EmbeddedServer,
                ['consul.client.host': consulServer.getHost(),
                 'consul.client.port': consulServer.getPort(),
                 'particle.application.name': serviceId,
                 'particle.heartbeat.interval':'1s'] // short heart beat interval
        )

        DiscoveryClient discoveryClient = application.applicationContext.getBean(DiscoveryClient)
        PollingConditions conditions = new PollingConditions(timeout: 30 )

        then:"The heart beat is received"
        conditions.eventually {
            Flowable.fromPublisher(discoveryClient.getInstances(serviceId)).blockingFirst().size() == 1
            Flowable.fromPublisher(discoveryClient.getInstances(serviceId)).blockingFirst().size() == 1
            MockConsulServer.passingReports.find { it.contains(serviceId)} != null
        }

        cleanup:
        application?.stop()
        consulServer?.stop()
    }
}
