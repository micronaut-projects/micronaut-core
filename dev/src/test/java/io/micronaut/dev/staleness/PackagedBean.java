package io.micronaut.dev.staleness;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "ConfigurationStalenessTest")
public class PackagedBean {
}
