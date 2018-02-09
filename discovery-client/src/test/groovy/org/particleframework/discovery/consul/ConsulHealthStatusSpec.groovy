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
import org.particleframework.discovery.consul.client.v1.ConsulClient
import org.particleframework.health.HealthStatus
import org.particleframework.http.HttpStatus
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.IgnoreIf
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
@IgnoreIf({ !System.getenv('CONSUL_HOST') && !System.getenv('CONSUL_PORT') })
class ConsulHealthStatusSpec extends Specification {

    void "test the consul service's health status is correct"() {
        given:

        String serviceId = 'myService'
        EmbeddedServer application = ApplicationContext.run(
                EmbeddedServer,
                ['consul.client.host': System.getenv('CONSUL_HOST'),
                 'consul.client.port': System.getenv('CONSUL_PORT'),
                 'particle.application.name': serviceId] // short heart beat interval
        )

        when:"An application is set to fail"

        ConsulClient consulClient = application.getApplicationContext().getBean(ConsulClient)
        HttpStatus status = Flowable.fromPublisher(consulClient.fail("service:myService:${application.port}")).blockingFirst()

        then:"The status is ok"
        status == HttpStatus.OK

        when:"The service is retrieved"
        def services = Flowable.fromPublisher(consulClient.getInstances(serviceId)).blockingFirst()

        then:"The service is down"
        services.size() == 1
        services[0].healthStatus == HealthStatus.DOWN

        cleanup:
        application?.stop()



    }
}
