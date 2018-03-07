/**
 * Configuration for Tomcat JDBC data sources
 */
@Configuration
@Requires(classes = DataSource.class)
package io.micronaut.configuration.jdbc.tomcat;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
import org.apache.tomcat.jdbc.pool.DataSource;
import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;