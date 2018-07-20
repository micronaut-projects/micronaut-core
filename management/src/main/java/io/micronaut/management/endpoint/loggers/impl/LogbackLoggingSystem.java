package io.micronaut.management.endpoint.loggers.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.loggers.LogLevel;
import io.micronaut.management.endpoint.loggers.LoggerConfiguration;
import io.micronaut.management.endpoint.loggers.LoggersEndpoint;
import io.micronaut.management.endpoint.loggers.LoggingSystem;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.stream.Collectors;

// TODO Implement methods of this class against logback.

/**
 * An implementation of {@link LoggingSystem} that works with logback.
 */
@Singleton
@Requires(beans = LoggersEndpoint.class)
public class LogbackLoggingSystem implements LoggingSystem {

    @Override
    public Collection<LoggerConfiguration> getLoggers() {
        return getLoggerContext()
                .getLoggerList()
                .stream()
                .map(LogbackLoggingSystem::toLoggerConfiguration)
                .collect(Collectors.toList());
    }

    @Override
    public LoggerConfiguration getLogger(String name) {
        return new LoggerConfiguration("foo", LogLevel.NOT_SPECIFIED,
                LogLevel.NOT_SPECIFIED);
    }

    @Override
    public void setLogLevel(String name, LogLevel level) {
    }

    /**
     * @return The logback {@link LoggerContext}
     */
    protected static LoggerContext getLoggerContext() {
        return (LoggerContext) LoggerFactory.getILoggerFactory();
    }

    /**
     * @param logger The logback {@link Logger} to convert
     * @return The converted {@link LoggerConfiguration}
     */
    protected static LoggerConfiguration toLoggerConfiguration(Logger logger) {
        return new LoggerConfiguration(
                logger.getName(),
                toLogLevel(logger.getLevel()),
                toLogLevel(logger.getEffectiveLevel())
        );
    }

    /**
     * @param level The logback {@link Level} to convert
     * @return The converted {@link LogLevel}
     */
    protected static LogLevel toLogLevel(Level level) {
        if (level == null) {
            return LogLevel.NOT_SPECIFIED;
        }
        else {
            return LogLevel.valueOf(level.toString());
        }
    }
}
