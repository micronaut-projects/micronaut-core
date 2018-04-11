/*
 * Copyright 2017 original authors
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
package io.micronaut.configuration.cassandra;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Cluster.Builder;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;

/**
 * Creates cassandra cluster for each configuration bean
 *
 * @author Nirav Assar
 * @since 1.0
 */
@Factory
public class ClusterFactory {

    @EachBean(CassandraConfiguration.class)
    @Bean(preDestroy = "close")
    public Cluster cassandraCluster(CassandraConfiguration cassandraConfiguration) {
        Builder b = Cluster.builder().addContactPoint(cassandraConfiguration.getNode());

        buildCluster(cassandraConfiguration, b);

        Cluster cluster = b.build();
        return cluster;
    }

    /**
     * Use the configured properties to build the cluster.
     *
     * @param cassandraConfiguration the read in properties
     * @param b builder object to create the cluster
     */
    private void buildCluster(CassandraConfiguration cassandraConfiguration, Builder b) {
        if (cassandraConfiguration.getPort() != null) {
            b.withPort(cassandraConfiguration.getPort());
        }

        if (cassandraConfiguration.getClusterName() != null) {
            b.withClusterName(cassandraConfiguration.getClusterName());
        }

        if (cassandraConfiguration.getUsername() != null && cassandraConfiguration.getPassword() != null) {
            b.withCredentials(cassandraConfiguration.getUsername(), cassandraConfiguration.getPassword());
        }

        if (cassandraConfiguration.getMaxSchemaAgreementWaitSeconds() != null) {
            b.withMaxSchemaAgreementWaitSeconds(cassandraConfiguration.getMaxSchemaAgreementWaitSeconds());
        }

        if (cassandraConfiguration.getWithoutJmxReporting()) {
            b.withoutJMXReporting();
        }

        if (cassandraConfiguration.getWithoutMetrics()) {
            b.withoutMetrics();
        }

        if (cassandraConfiguration.getSslEnabled()) {
            b.withSSL();
        }
    }

}