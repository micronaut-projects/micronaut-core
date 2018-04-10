package io.micronaut.configuration.cassandra

import com.datastax.driver.core.Cluster
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class CassandraConfigurationSpec extends Specification {

    void "test no configuration"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.start()

        expect: "No beans are created"
        !applicationContext.containsBean(CassandraConfiguration)
        !applicationContext.containsBean(Cluster)

        cleanup:
        applicationContext.close()
    }

    void "test single cluster connection"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'test',
                ['cassandra.primary.node': "127.0.0.1",
                 'cassandra.primary.port': 9042]
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(CassandraConfiguration)
        applicationContext.containsBean(Cluster)

        when:
        Cluster cluster = applicationContext.getBean(Cluster)
        List<InetSocketAddress> inetSocketAddresses = cluster.manager.contactPoints

        then:
        cluster.getClusterName() == "cluster1"
        inetSocketAddresses[0].getHostName() == "127.0.0.1"
        inetSocketAddresses[0].getPort() == 9042

        cleanup:
        applicationContext.close()
    }

    void "test multiple cluster connections"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'test',
                ['cassandra.primary.node': "127.0.0.1",
                 'cassandra.primary.port': 9042,
                 'cassandra.secondary.node': "127.0.0.2",
                 'cassandra.secondary.port': 9043]
        ))
        applicationContext.start()

        when:
        Cluster primaryCluster = applicationContext.getBean(Cluster, Qualifiers.byName("primary"))
        Cluster secondaryCluster = applicationContext.getBean(Cluster, Qualifiers.byName("secondary"))
        List<InetSocketAddress> primaryInetSocketAddresses = primaryCluster.manager.contactPoints
        List<InetSocketAddress> secondaryInetSocketAddresses = secondaryCluster.manager.contactPoints

        then:
        primaryInetSocketAddresses[0].getHostName() == "127.0.0.1"
        primaryInetSocketAddresses[0].getPort() == 9042

        secondaryInetSocketAddresses[0].getHostName() == "127.0.0.2"
        secondaryInetSocketAddresses[0].getPort() == 9043

        cleanup:
        applicationContext.close()
    }
}
