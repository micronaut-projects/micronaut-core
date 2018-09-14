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
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * @author graemerocher
 * @since 1.0
 */
@IgnoreIf({ !env['EUREKA_HOST'] && !env['EUREKA_PORT'] })
class EurekaAutoRegistrationSpec extends Specification{

    @Ignore("https://github.com/micronaut-projects/micronaut-core/issues/566")
    void "test that an application can be registered and de-registered with Eureka"() {
        when: "An application is started and eureka configured"
        String serviceId = 'myService'
        def eurekaConfiguration = [
                'eureka.client.host'                       : System.getenv('EUREKA_HOST'),
                'eureka.client.port'                       : System.getenv('EUREKA_PORT')
        ]
        // run an application
        EmbeddedServer application = ApplicationContext.run(
                EmbeddedServer,
                ['consul.client.registration.enabled'              : false,
                 'micronaut.application.name'                : serviceId] + eurekaConfiguration
        )

        // run a Eureka client
        EurekaClient eurekaClient = ApplicationContext.build(eurekaConfiguration).run(EurekaClient)

        // since Eureka is eventually consistent a long timeout/delay is required slowing this test down significantly
        PollingConditions conditions = new PollingConditions(timeout: 30, delay: 1)

        then: "The application is registered"
        conditions.eventually {
            Flowable.fromPublisher(eurekaClient.getInstances(serviceId)).blockingFirst().size() == 1
            // Eureka uses upper case for application names
            Flowable.fromPublisher(eurekaClient.getServiceIds()).blockingFirst().contains(serviceId.toUpperCase())
        }



        when: "The application is stopped"
        application?.stop()

        then: "The application is de-registered"
        true
        conditions.eventually {
            Flowable.fromPublisher(eurekaClient.getInstances(serviceId)).blockingFirst().size() == 0
        }

    }
}
