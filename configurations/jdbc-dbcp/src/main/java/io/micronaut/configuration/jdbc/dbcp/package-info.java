/**
 * Configuration for Apache DBCP data sources
 */
@Configuration
@Requires(classes = BasicDataSource.class)
package io.micronaut.configuration.jdbc.dbcp;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
import org.apache.commons.dbcp2.BasicDataSource;
import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;