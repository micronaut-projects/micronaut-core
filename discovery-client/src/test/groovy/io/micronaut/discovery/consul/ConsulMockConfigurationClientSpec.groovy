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
package io.micronaut.discovery.consul

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.discovery.consul.client.v1.KeyValue
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

/**
 * @author graemerocher
 * @since 1.0
 */
@Stepwise
class ConsulMockConfigurationClientSpec extends Specification {
    @Shared
    int serverPort = SocketUtils.findAvailableTcpPort()

    @AutoCleanup
    @Shared
    EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer, [
            'micronaut.server.port'                   : serverPort,
            (MockConsulServer.ENABLED):true
    ])


    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ['consul.client.host'                     : 'localhost',
             'consul.client.port'                     : serverPort]
    )

    @Shared
    ConsulClient client = embeddedServer.applicationContext.getBean(ConsulClient)


    void "test read and write key values with ConsulClient"() {
        when:"A property is written"
        def result = Flowable.fromPublisher(client.putValue("/config/application/datasource.url", "mysql://blah")).blockingFirst()

        then:"The operation was successful"
        result

        when:"Properties are read"
        Flowable.fromPublisher(client.putValue("/config/application/datasource.driver", "java.SomeDriver")).blockingFirst()

        List<KeyValue> keyValues = Flowable.fromPublisher(client.readValues("/config")).blockingFirst()

        then:
        keyValues.size() == 2
    }

    void "test discovery property sources from Consul"() {


        when:
        def env = Mock(Environment)
        env.getActiveNames() >> (['test'] as Set)
        List<PropertySource> propertySources = Flowable.fromPublisher(client.getPropertySources(env)).toList().blockingGet()

        then:"TODO: WIP"
        propertySources.size() == 0
    }
}
