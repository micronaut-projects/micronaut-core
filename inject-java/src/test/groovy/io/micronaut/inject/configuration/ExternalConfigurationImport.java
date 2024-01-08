
package io.micronaut.inject.configuration;

import io.micronaut.context.annotation.Import;
import io.micronaut.inject.test.external.ExternalConfiguration;

@Import(classes = ExternalConfiguration.class)
public class ExternalConfigurationImport {
}
