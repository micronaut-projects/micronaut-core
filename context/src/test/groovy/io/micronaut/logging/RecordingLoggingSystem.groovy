package io.micronaut.logging

import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

/**
 * Records the levels the configurer sets, to tell how often a refresh applied them.
 */
@Singleton
@Requires(property = "spec.name", value = "PropertiesLoggingLevelsConfigurerSpec.refresh")
class RecordingLoggingSystem implements LoggingSystem {
    final List<String> set = []

    @Override
    void setLogLevel(String name, LogLevel level) {
        set << name + "=" + level
    }
}
