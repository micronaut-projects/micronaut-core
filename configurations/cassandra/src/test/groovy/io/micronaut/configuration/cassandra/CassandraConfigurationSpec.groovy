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
        // tag::single[]
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'test',
                ['cassandra.default.clusterName': "ociCluster",
                 'cassandra.default.contactPoint': "localhost",
                 'cassandra.default.port': 9042,
                 'cassandra.default.maxSchemaAgreementWaitSeconds': 20,
                 'cassandra.default.ssl': true]
        ))
        applicationContext.start()
        // end::single[]

        expect:
        applicationContext.containsBean(CassandraConfiguration)
        applicationContext.containsBean(Cluster)

        when:
        Cluster cluster = applicationContext.getBean(Cluster)
        List<InetSocketAddress> inetSocketAddresses = cluster.manager.contactPoints

        then:
        cluster.getClusterName() == "ociCluster"
        inetSocketAddresses[0].getHostName() == "localhost"
        inetSocketAddresses[0].getPort() == 9042
        cluster.getConfiguration().getProtocolOptions().getMaxSchemaAgreementWaitSeconds() == 20
        cluster.getConfiguration().getProtocolOptions().getSSLOptions() != null


        cleanup:
        applicationContext.close()
    }

    void "test multiple cluster connections"() {
        given:
        // tag::multiple[]
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'test',
                ['cassandra.default.contactPoint': "localhost",
                 'cassandra.default.port': 9042,
                 'cassandra.secondary.contactPoint': "127.0.0.2",
                 'cassandra.secondary.port': 9043]
        ))
        applicationContext.start()
        // end::multiple[]

        when:
        Cluster defaultCluster = applicationContext.getBean(Cluster)
        Cluster secondaryCluster = applicationContext.getBean(Cluster, Qualifiers.byName("secondary"))
        List<InetSocketAddress> defaultInetSocketAddresses = defaultCluster.manager.contactPoints
        List<InetSocketAddress> secondaryInetSocketAddresses = secondaryCluster.manager.contactPoints

        then:
        defaultInetSocketAddresses[0].getHostName() == "localhost"
        defaultInetSocketAddresses[0].getPort() == 9042

        secondaryInetSocketAddresses[0].getHostName() == "127.0.0.2"
        secondaryInetSocketAddresses[0].getPort() == 9043

        cleanup:
        applicationContext.close()
    }
}
