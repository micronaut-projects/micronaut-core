package io.micronaut.management.endpoint.loggers.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.loggers.LogLevel;
import io.micronaut.management.endpoint.loggers.LoggerConfiguration;
import io.micronaut.management.endpoint.loggers.LoggersEndpoint;
import io.micronaut.management.endpoint.loggers.LoggingSystem;

import javax.inject.Singleton;
import java.util.stream.Stream;

@Singleton
@Requires(beans = LoggersEndpoint.class)
public class LogbackLoggingSystem implements LoggingSystem {

    @Override
    public Stream<LoggerConfiguration> getLoggers() {
        return Stream.empty();
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
