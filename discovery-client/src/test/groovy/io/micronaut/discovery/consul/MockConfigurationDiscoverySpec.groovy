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
import io.micronaut.discovery.config.ConfigurationClient
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class MockConfigurationDiscoverySpec extends Specification {

    @AutoCleanup
    @Shared
    EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer, [
            (MockConsulServer.ENABLED): true
    ])

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
    }

    void 'test read application configuration from Consul'() {
        given:
        System.setProperty("some.consul.value", "other") // consul should override
        writeValue("application", "some.consul.value", "test") // should not use default
        writeValue("application,test", "some.consul.value", "foobar")
        writeValue("application,other", "some.consul.value", "other") // should not use test env
        writeValue("application", "must.override1", "test")
        writeValue("test-app", "must.override1", "overridden")
        writeValue("test-app", "must.override2", "test")
        writeValue("test-app,test", "must.override2", "overridden")


        ApplicationContext applicationContext = ApplicationContext.run(
                [
                        (ConfigurationClient.ENABLED): true,
                        'micronaut.application.name' :'test-app',
                        'consul.client.host'         : 'localhost',
                        'consul.client.port'         : consulServer.port]
        )

        when:"A configuration value is read"
        def environment = applicationContext.environment
        def result = environment.getProperty("some.consul.value", String)

        then:"the value is the correct one from Consul"
        result.isPresent()
        result.get() == 'foobar'
        environment.getProperty('must.override1', String).get() == 'overridden'
        environment.getProperty('must.override2', String).get() == 'overridden'

        when:"a value is changed and the environment is refreshed"
        writeValue("test-app", "must.override1", "changed")
        environment.refresh()

        then:"The value is retrieved again"
        environment.getProperty('must.override1', String).get() == 'changed'
    }

    void "test multiple environment precedence"() {
        System.setProperty("some.consul.value", "other") // consul should override

        writeValue("test-app,second", "some.consul.value-1", "1")

        writeValue("application,second", "some.consul.value-1", "2")
        writeValue("application,second", "some.consul.value-2", "1")

        writeValue("test-app,first", "some.consul.value-1", "3")
        writeValue("test-app,first", "some.consul.value-2", "2")
        writeValue("test-app,first", "some.consul.value-3", "1")

        writeValue("application,first", "some.consul.value-1", "4")
        writeValue("application,first", "some.consul.value-2", "3")
        writeValue("application,first", "some.consul.value-3", "2")
        writeValue("application,first", "some.consul.value-4", "1")

        writeValue("test-app", "some.consul.value-1", "5")
        writeValue("test-app", "some.consul.value-2", "4")
        writeValue("test-app", "some.consul.value-3", "3")
        writeValue("test-app", "some.consul.value-4", "2")
        writeValue("test-app", "some.consul.value-5", "1")

        writeValue("application", "some.consul.value-1", "6")
        writeValue("application", "some.consul.value-2", "5")
        writeValue("application", "some.consul.value-3", "4")
        writeValue("application", "some.consul.value-4", "3")
        writeValue("application", "some.consul.value-5", "2")
        writeValue("application", "some.consul.value-6", "1")

        ApplicationContext applicationContext = ApplicationContext.run(
                [
                        (ConfigurationClient.ENABLED): true,
                        'micronaut.application.name' :'test-app',
                        'consul.client.host'         : 'localhost',
                        'consul.client.port'         : consulServer.port
                ], "first", "second")

        when:"A configuration value is read"
        def environment = applicationContext.environment

        then:
        environment.getRequiredProperty("some.consul.value-1", String) == "1"
        environment.getRequiredProperty("some.consul.value-2", String) == "1"
        environment.getRequiredProperty("some.consul.value-3", String) == "1"
        environment.getRequiredProperty("some.consul.value-4", String) == "1"
        environment.getRequiredProperty("some.consul.value-5", String) == "1"
        environment.getRequiredProperty("some.consul.value-6", String) == "1"

        cleanup:
        applicationContext.close()
    }


    void 'test disable application configuration from Consul'() {
        given:
        writeValue("application", "some.consul.value2", "test") // should not use default
        writeValue("application,test", "some.consul.value", "foobar")
        writeValue("application,other", "some.consul.value", "other") // should not use test env
        ApplicationContext applicationContext = ApplicationContext.run(
                [
                        'consul.client.config.enabled': false,
                        'consul.client.host'          : 'localhost',
                        'consul.client.port'          : consulServer.port]
        )

        def result = applicationContext.environment.getProperty("some.consul.value2", String)
        expect:
        !result.isPresent()

    }

    private void writeValue(String env, String name, String value) {
        Flowable.fromPublisher(client.putValue("/config/$env/$name", value)).blockingFirst()
    }
}
