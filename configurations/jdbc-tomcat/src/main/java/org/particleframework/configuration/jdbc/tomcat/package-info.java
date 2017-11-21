/**
 * Configuration for Tomcat JDBC data sources
 */
@Configuration
@Requires(classes = DataSource.class)
package org.particleframework.configuration.jdbc.tomcat;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.particleframework.context.annotation.Configuration;
import org.particleframework.context.annotation.Requires;