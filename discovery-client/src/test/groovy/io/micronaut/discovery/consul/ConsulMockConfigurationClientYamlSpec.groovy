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
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.discovery.config.ConfigurationClient
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.discovery.consul.config.ConsulConfigurationClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ConsulMockConfigurationClientYamlSpec extends Specification {

    @AutoCleanup
    @Shared
    EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer, [
            (MockConsulServer.ENABLED): true
    ])


    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            [
                    (ConfigurationClient.ENABLED): true,
                    'consul.client.config.format': 'yaml',
                    'consul.client.host'         : 'localhost',
                    'consul.client.port'         : consulServer.getPort()]
    )

    @Shared
    ConsulClient client = embeddedServer.applicationContext.getBean(ConsulClient)

    @Shared
    ConsulConfigurationClient configClient = embeddedServer.applicationContext.getBean(ConsulConfigurationClient)

    def setup() {
        consulServer.applicationContext.getBean(MockConsulServer)
                .keyvalues.clear()
    }

    void "test discovery property sources from Consul with YAML handling"() {

        given:
        writeValue("application", """
datasource:
    url: "mysql://blah"
    driver: "java.SomeDriver"
""")
        writeValue("application,test", """
foo: bar
""")
        writeValue("application,other", """
foo: baz 
""")
        when:
        def env = Mock(Environment)
        env.getActiveNames() >> (['test'] as Set)
        List<PropertySource> propertySources = Flowable.fromPublisher(configClient.getPropertySources(env)).toList().blockingGet()

        then: "verify property source characteristics"
        propertySources.size() == 2
        propertySources[0].order > EnvironmentPropertySource.POSITION
        propertySources[0].name == 'consul-application'
        propertySources[0].get('datasource.url') == "mysql://blah"
        propertySources[0].get('datasource.driver') == "java.SomeDriver"
        propertySources[0].toList().size() == 2
        propertySources[1].name == 'consul-application[test]'
        propertySources[1].get("foo") == "bar"
        propertySources[1].order > propertySources[0].order
        propertySources[1].toList().size() == 1
    }

    void "test discovery property sources from Consul with invalid YAML"() {

        given:
        writeValue("application", """
datasource:
    url: "mysql://blah
    driver: "java.SomeDriver"
""")
        when:
        def env = Mock(Environment)
        env.getActiveNames() >> (['test'] as Set)
        List<PropertySource> propertySources = Flowable.fromPublisher(configClient.getPropertySources(env)).toList().blockingGet()

        then: "verify property source characteristics"
        def e = thrown(ConfigurationException)
        e.message.startsWith("Error reading property source [application]")
    }



    private void writeValue(String name, String value) {
        Flowable.fromPublisher(client.putValue("config/$name", value)).blockingFirst()
    }
}