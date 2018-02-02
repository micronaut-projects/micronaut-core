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
import org.particleframework.discovery.CompositeDiscoveryClient
import org.particleframework.discovery.DiscoveryClient
import org.particleframework.discovery.eureka.client.v2.ApplicationInfo
import org.particleframework.discovery.eureka.client.v2.EurekaClient
import org.particleframework.discovery.eureka.client.v2.InstanceInfo
import org.particleframework.http.HttpStatus
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.*
import spock.util.concurrent.PollingConditions

import javax.validation.ConstraintViolationException

/**
 * @author graemerocher
 * @since 1.0
 */
@IgnoreIf({ !System.getenv('EUREKA_HOST') && !System.getenv('EUREKA_PORT')})
@Stepwise
class EurekaClientSpec extends Specification {

    @AutoCleanup @Shared EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            [(EurekaConfiguration.HOST): System.getenv('EUREKA_HOST'),
             (EurekaConfiguration.PORT): System.getenv('EUREKA_PORT'),
             'eureka.client.readTimeout': '5s']
    )
    @Shared EurekaClient client = embeddedServer.applicationContext.getBean(EurekaClient)
    @Shared DiscoveryClient discoveryClient = embeddedServer.applicationContext.getBean(DiscoveryClient)

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

    void "test register and de-register instance"() {

        given:
        PollingConditions conditions = new PollingConditions(timeout: 10)

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
