package io.micronaut.discovery.consul

import groovy.transform.NotYetImplemented
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MockConfigurationDiscoverySpec extends Specification {

    @Shared
    int serverPort = SocketUtils.findAvailableTcpPort()

    @AutoCleanup
    @Shared
    EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer, [
            'micronaut.server.port'   : serverPort,
            (MockConsulServer.ENABLED): true
    ])

    @AutoCleanup
    @Shared
    ApplicationContext someContext = ApplicationContext.run(
            [
                    'consul.client.host': 'localhost',
                    'consul.client.port': serverPort]
    )

    @Shared
    ConsulClient client = someContext.getBean(ConsulClient)

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
                        'consul.client.config.enabled': true,
                        'micronaut.application.name':'test-app',
                        'consul.client.host': 'localhost',
                        'consul.client.port': serverPort]
        )

        def environment = applicationContext.environment
        def result = environment.getProperty("some.consul.value", String)

        expect:
        result.isPresent()
        result.get() == 'foobar'
        environment.getProperty('must.override1', String).get() == 'overridden'
        environment.getProperty('must.override2', String).get() == 'overridden'

        cleanup:
        System.setProperty('some.consul.value','')
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
                        'consul.client.port'          : serverPort]
        )

        def result = applicationContext.environment.getProperty("some.consul.value2", String)
        expect:
        !result.isPresent()

    }

    private void writeValue(String env, String name, String value) {
        Flowable.fromPublisher(client.putValue("/config/$env/$name", value)).blockingFirst()
    }
}
