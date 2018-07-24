package io.micronaut.management.endpoint.loggers.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.loggers.*;
import io.reactivex.Single;
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
public class RxLoggersManager implements LoggersManager<Map<String, Object>> {

    @Override
    public Publisher<Map<String, Object>> getLoggers(LoggingSystem loggingSystem) {
        Map<String, Object> data = new HashMap<>(2);

        data.put("levels", getLogLevels());
        data.put("loggers", getLoggerData(loggingSystem.getLoggers()));

        return Single.just(data).toFlowable();
    }

    @Override
    public Publisher<Map<String, Object>> getLogger(LoggingSystem loggingSystem,
                                                    String name) {
        return Single.just(getLoggerData(loggingSystem.getLogger(name)))
                .toFlowable();
    }

    @Override
    public void setLogLevel(LoggingSystem loggingSystem, String name, LogLevel level) {
        loggingSystem.setLogLevel(name, level);
    }

    /**
     * @param configurations The logger configurations
     * @return A Map from logger name to logger configuration data
     */
    protected static Map<String, Object> getLoggerData(
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
    protected static Map<String, Object> getLoggerData(
            LoggerConfiguration configuration) {
        return configuration.getData();
    }

    /**
     * @return A list with all {@link LogLevel} values
     */
    protected static List<LogLevel> getLogLevels() {
        return Arrays.asList(LogLevel.values());
    }

}
