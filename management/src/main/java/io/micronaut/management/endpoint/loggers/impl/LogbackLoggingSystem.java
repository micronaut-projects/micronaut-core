package io.micronaut.management.endpoint.loggers.impl;

import io.micronaut.management.endpoint.loggers.LogLevel;
import io.micronaut.management.endpoint.loggers.LoggerConfiguration;
import io.micronaut.management.endpoint.loggers.LoggingSystem;

import javax.inject.Singleton;
import java.util.stream.Stream;

@Singleton
public class LogbackLoggingSystem implements LoggingSystem {

    public Stream<LoggerConfiguration> getLoggers() {
        return Stream.empty();
    }

    public LoggerConfiguration getLogger(String name) {
        return new LoggerConfiguration("foo", LogLevel.NOT_SPECIFIED,
                LogLevel.NOT_SPECIFIED);
    }

    public void setLogLevel(String name, LogLevel level) {

    }

}
