/**
 * Injection point bean definitions exposed as more than one generic type, and disposed of with their consumer.
 */
@Configuration
@Requires(property = "spec", value = "ExposedInjectionPointBeanDefinitionSpec")
package io.micronaut.inject.provider.exposedtypes;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.context.annotation.Requires;
