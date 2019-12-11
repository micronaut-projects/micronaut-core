/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.discovery.config.ConfigurationClient
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

/**
 * @author graemerocher
 * @since 1.0
 */
@RestoreSystemProperties
class ConsulMockConfigurationClientFilesSpec extends Specification {

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
                    'micronaut.application.name'  : 'test-app',
                    'consul.client.config.format' : 'file',
                    'consul.client.host'          : 'localhost',
                    'consul.client.port'          : consulServer.getPort()]
    )

    @AutoCleanup
    @Shared
    ApplicationContext someContext = ApplicationContext.run(
            [
                    'consul.client.host': 'localhost',
                    'consul.client.port': consulServer.getPort()]
    )

    @Shared
    ConsulClient client = someContext.getBean(ConsulClient)

    def setup() {
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "true")
        consulServer.applicationContext.getBean(MockConsulServer)
                .keyvalues.clear()
    }

    void "test discovery property sources from Consul with YAML handling"() {

        given:
        writeValue("application.properties", """
datasource.url=mysql://blah
datasource.driver=java.SomeDriver
""")
        writeValue("application-test.json", """
{ "some": "value" }
""")
        writeValue("application-other.properties", """
also.not.here=true
""")
        writeValue("test-app-test.yml", """
datasource:
    url: mysql://overridden
""")
        writeValue("test-app-other.yml", """
not:
    here: true 
""")

        ApplicationContext applicationContext = ApplicationContext.run(
                [
                        (ConfigurationClient.ENABLED): true,
                        'consul.client.config.path':'some-path/config',
                        'consul.client.config.format': 'file',
                        'micronaut.application.name':'test-app',
                        'consul.client.host': 'localhost',
                        'consul.client.port': consulServer.getPort()]
        )
        ConfigurationClient configurationClient = applicationContext.getBean(ConfigurationClient)

        when:
        def mockEnv = Mock(Environment)
        mockEnv.getActiveNames() >> (['test'] as Set)
        List<PropertySource> propertySources = Flowable.fromPublisher(configurationClient.getPropertySources(mockEnv)).toList().blockingGet()

        then: "verify property source characteristics"
        propertySources.size() == 3


        when:"The real environment is obtained"
        Environment env = applicationContext.getEnvironment()

        then:"The environment is correct"
        env.getRequiredProperty('some', String) == 'value'
        env.getRequiredProperty('datasource.url', String) == 'mysql://overridden'
        env.getRequiredProperty('datasource.driver', String) == 'java.SomeDriver'
        !env.getProperty('not.there', Boolean).isPresent()
        !env.getProperty('also.not.there', Boolean).isPresent()
    }


    private void writeValue(String name, String value) {
        Flowable.fromPublisher(client.putValue("some-path/config/$name", value)).blockingFirst()
    }
}
