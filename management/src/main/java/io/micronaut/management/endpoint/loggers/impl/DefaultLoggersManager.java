package io.micronaut.management.endpoint.loggers.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.loggers.*;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link LoggersManager}.
 *
 * @author Matthew Moss
 * @since 1.0
 */
@Singleton
@Requires(beans = LoggersEndpoint.class)
public class DefaultLoggersManager implements LoggersManager<Map<String, Object>> {

    private static final String LEVELS = "levels";
    private static final String LOGGERS = "loggers";

    @Override
    public Publisher<Map<String, Object>> getLoggers(LoggingSystem loggingSystem) {
        Map<String, Object> data = new HashMap<>(2);

        data.put(LEVELS, getLogLevels());
        data.put(LOGGERS, getLoggerData(loggingSystem.getLoggers()));

        return Flowable.just(data);
    }

    @Override
    public Publisher<Map<String, Object>> getLogger(LoggingSystem loggingSystem,
                                                    String name) {
        return Flowable.just(getLoggerData(loggingSystem.getLogger(name)));
    }

    @Override
    public void setLogLevel(LoggingSystem loggingSystem, String name, LogLevel level) {
        loggingSystem.setLogLevel(name, level);
    }

    /**
     * @param configurations The logger configurations
     * @return A Map from logger name to logger configuration data
     */
    private static Map<String, Object> getLoggerData(
            Collection<LoggerConfiguration> configurations) {
        return configurations
                .stream()
                .collect(Collectors.toMap(
                        LoggerConfiguration::getName,
                        LoggerConfiguration::getData));
    }

    /**
     * @param configuration The logger configuration
     * @return The logger configuration data
     */
    private static Map<String, Object> getLoggerData(
            LoggerConfiguration configuration) {
        return configuration.getData();
    }

    /**
     * @return A list with all {@link LogLevel} values
     */
    private static List<LogLevel> getLogLevels() {
        return Arrays.asList(LogLevel.values());
    }

}
