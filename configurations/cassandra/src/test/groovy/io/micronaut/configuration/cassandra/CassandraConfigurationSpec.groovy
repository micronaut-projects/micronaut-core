package io.micronaut.configuration.cassandra

import com.datastax.driver.core.Cluster
import com.datastax.driver.core.ResultSet
import com.datastax.driver.core.Session
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.MapPropertySource
import spock.lang.Specification

class CassandraConfigurationSpec extends Specification {

    void "test something"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'test',
                ['cassandra.primary.node': "localhost"
                 /*'cassandra.primary.port': 9042*/]
        ))
        applicationContext.start()

        expect:
        1 == 1

        when:
        CassandraConfiguration cassandraConfiguration = applicationContext.getBean(CassandraConfiguration)
        println "*******************" + cassandraConfiguration.dump()
        Cluster cluster = applicationContext.getBean(Cluster)
        Session session = cluster.connect()
        ResultSet rs = session.execute("SELECT * FROM system_schema.keyspaces")
        println "RESULTS *********** " + rs.dump()

        then:
        1 == 1

        cleanup:
        applicationContext.close()
    }
}
