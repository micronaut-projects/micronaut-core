/**
 * Configuration for Cassandra
 */
@Configuration
@Requires(classes = Cluster.class)
package io.micronaut.configuration.cassandra;

import com.datastax.driver.core.Cluster;
import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;