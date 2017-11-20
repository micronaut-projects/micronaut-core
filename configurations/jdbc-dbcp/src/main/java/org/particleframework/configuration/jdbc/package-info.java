/**
 * Configuration for Apache DBCP data sources
 */
@Configuration
@Requires(classes = BasicDataSource.class)
package org.particleframework.configuration.jdbc;

import org.apache.commons.dbcp2.BasicDataSource;
import org.particleframework.context.annotation.Configuration;
import org.particleframework.context.annotation.Requires;