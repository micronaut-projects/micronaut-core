/**
 * A bean configuration whose package-level requirement reads the configuration, for ConfigurationStalenessTest.
 */
@Configuration
@Requires(missingProperty = "staleness.package")
package io.micronaut.dev.staleness;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
