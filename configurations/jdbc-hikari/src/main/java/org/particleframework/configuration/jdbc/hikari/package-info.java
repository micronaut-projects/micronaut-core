/**
 * Configuration for Hikari data sources
 */
@Configuration
@Requires(classes = HikariDataSource.class)
package org.particleframework.configuration.jdbc.hikari;

import com.zaxxer.hikari.HikariDataSource;
import org.particleframework.context.annotation.Configuration;
import org.particleframework.context.annotation.Requires;