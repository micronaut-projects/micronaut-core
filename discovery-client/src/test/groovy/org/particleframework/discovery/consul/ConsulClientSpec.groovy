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
import spock.lang.IgnoreIf
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
@IgnoreIf({ !System.getenv('CONSUL_HOST') && !System.getenv('CONSUL_PORT')})
class ConsulClientSpec extends Specification {


    void "test consul client"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'consul.host': System.getenv('CONSUL_HOST'),
                'consul.port': System.getenv('CONSUL_PORT')
        )
        ConsulClient client = applicationContext.getBean(ConsulClient)

        when:
        Map serviceNames = Flowable.fromPublisher(client.serviceNames).blockingFirst()

        then:
        serviceNames
        serviceNames.containsKey("consul")
    }
}
