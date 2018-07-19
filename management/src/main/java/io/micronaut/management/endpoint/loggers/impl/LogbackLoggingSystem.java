package io.micronaut.management.endpoint.loggers.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.loggers.LogLevel;
import io.micronaut.management.endpoint.loggers.LoggerConfiguration;
import io.micronaut.management.endpoint.loggers.LoggersEndpoint;
import io.micronaut.management.endpoint.loggers.LoggingSystem;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.Collections;

// TODO Implement methods of this class against logback.

/**
 * An implementation of {@link LoggingSystem} that works with logback.
 */
@Singleton
@Requires(beans = LoggersEndpoint.class)
public class LogbackLoggingSystem implements LoggingSystem {

    @Override
    public Collection<LoggerConfiguration> getLoggers() {
        return Collections.singletonList(
                new LoggerConfiguration("foo", LogLevel.NOT_SPECIFIED,
                        LogLevel.NOT_SPECIFIED)
        );
    }

    @Override
    public LoggerConfiguration getLogger(String name) {
        return new LoggerConfiguration("foo", LogLevel.NOT_SPECIFIED,
                LogLevel.NOT_SPECIFIED);
    }

    @Override
    public void setLogLevel(String name, LogLevel level) {
    }

}
