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
package io.micronaut.configuration.cassandra;

import com.datastax.driver.core.Cluster;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;

/**
 * Creates cassandra cluster for each configuration bean.
 *
 * @author Nirav Assar
 * @since 1.0
 */
@Factory
public class ClusterFactory {

    /**
     * Creates the {@link Cluster.Builder} bean for the given configuration.
     *
     * @param cassandraConfiguration The cassandra configuration bean
     * @return A {@link Cluster.Builder} bean
     */

    @EachBean(CassandraConfiguration.class)
    Cluster.Builder cassandraBuilder(CassandraConfiguration cassandraConfiguration) {
        return cassandraConfiguration.getBuilder();
    }

    /**
     * Creates the {@link Cluster} bean for the given configuration.
     *
     * @param builder The {@link Cluster.Builder}
     * @return A {@link Cluster} bean
     */
    @EachBean(Cluster.Builder.class)
    @Bean(preDestroy = "close")
    public Cluster cassandraCluster(Cluster.Builder builder) {
        return builder.build();
    }
}
