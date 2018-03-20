package io.micronaut.discovery.consul

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.env.EnvironmentPropertySource
import io.micronaut.context.env.PropertySource
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ConsulMockConfigurationClientPropertiesSpec extends Specification {
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
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            [
                    'consul.client.config.format': 'properties',
                    'consul.client.host'         : 'localhost',
                    'consul.client.port'         : serverPort]
    )

    @Shared
    ConsulClient client = embeddedServer.applicationContext.getBean(ConsulClient)


    def setup() {
        consulServer.applicationContext.getBean(MockConsulServer)
                .keyvalues.clear()
    }

    void "test discovery property sources from Consul with Properties file handling"() {

        given:
        writeValue("application", """
datasource.url=mysql://blah
datasource.driver=java.SomeDriver
""")
        writeValue("application,test", """
foo=bar
""")
        writeValue("application,other", """
foo=baz 
""")
        when:
        def env = Mock(Environment)
        env.getActiveNames() >> (['test'] as Set)
        List<PropertySource> propertySources = Flowable.fromPublisher(client.getPropertySources(env)).toList().blockingGet()

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



    private void writeValue(String name, String value) {
        Flowable.fromPublisher(client.putValue("/config/$name", value)).blockingFirst()
    }
}
