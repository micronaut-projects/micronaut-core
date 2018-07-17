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
package io.micronaut.discovery.consul

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.env.EnvironmentPropertySource
import io.micronaut.context.env.PropertySource
import io.micronaut.discovery.config.ConfigurationClient
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.discovery.consul.config.ConsulConfigurationClient
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
class ConsulMockConfigurationClientNativeSpec extends Specification {
    @AutoCleanup
    @Shared
    EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer, [
            (MockConsulServer.ENABLED):true
    ])

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            [
                    (ConfigurationClient.ENABLED): true,
                    'consul.client.host': 'localhost',
                    'consul.client.port': consulServer.getPort()]
    )

    @Shared
    ConsulClient client = embeddedServer.applicationContext.getBean(ConsulClient)

    @Shared
    ConsulConfigurationClient configClient = embeddedServer.applicationContext.getBean(ConsulConfigurationClient)

    void "test read and write key values with ConsulClient"() {
        when:"A property is written"
        def result = Flowable.fromPublisher(client.putValue("config/application/datasource.url", "mysql://blah")).blockingFirst()

        then:"The operation was successful"
        result

        when:"Properties are read"
        Flowable.fromPublisher(client.putValue("config/application/datasource.driver", "java.SomeDriver")).blockingFirst()

        List<KeyValue> keyValues = Flowable.fromPublisher(client.readValues("config")).blockingFirst()

        then:
        keyValues.size() == 2
    }

    void "test discovery property sources from Consul with native property handling"() {

        given:
        writeValue("application,test", "foo", "bar")
        writeValue("application,other", "foo", "baz")
        writeValue("application,other", "more", "stuff")

        when:
        def env = Mock(Environment)
        env.getActiveNames() >> (['test'] as Set)
        List<PropertySource> propertySources = Flowable.fromPublisher(configClient.getPropertySources(env)).toList().blockingGet()

        then:"verify property source characteristics"
        propertySources.size() == 2
        propertySources[0].order > EnvironmentPropertySource.POSITION
        propertySources[0].name == 'consul-application'
        propertySources[0].get('datasource.url') == "mysql://blah"
        propertySources[0].get('datasource.driver') == "java.SomeDriver"
        propertySources[0].toList().size() == 2
        propertySources[1].name == 'consul-application[test]'
        propertySources[1].order > propertySources[0].order
        propertySources[1].toList().size() == 1
    }

    private void writeValue(String env, String name, String value) {
        Flowable.fromPublisher(client.putValue("config/$env/$name", value)).blockingFirst()
    }
}
