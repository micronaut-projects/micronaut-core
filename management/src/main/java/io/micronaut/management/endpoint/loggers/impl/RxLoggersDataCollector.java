package io.micronaut.management.endpoint.loggers.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.loggers.*;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.*;

/**
 * Default implementation of {@link LoggersDataCollector}.
 */
@Singleton
@Requires(beans = LoggersEndpoint.class)
public class RxLoggersDataCollector
        implements LoggersDataCollector<Map<String, Object>> {

    @Override
    public Publisher<Map<String, Object>> getAll(LoggingSystem loggingSystem) {
        return Single.zip(getLoggers(loggingSystem.getLoggers()), getLogLevels(),
                (loggers, levels) -> {
                    Map<String, Object> data = new HashMap<>(2);
                    data.put("loggers", loggers);
                    data.put("levels", levels);
                    return data;
                })
                .toFlowable();
    }

    @Override
    public Publisher<Map<String, Object>> getOne(LoggingSystem loggingSystem,
                                                 String name) {
        return getLogger(loggingSystem.getLogger(name)).toFlowable();
    }

    @Override
    public void setLogLevel(LoggingSystem loggingSystem, String name, String level) {
        // TODO Make reactive?
        loggingSystem.setLogLevel(name, toLogLevel(level));
    }

    /**
     * @param configurations The logger configurations
     * @return A {@link Single} that wraps a Map
     */
    protected static Single<Map<String, Object>> getLoggers(
            Collection<LoggerConfiguration> configurations) {
        Map<String, Object> loggers = new HashMap<>(configurations.size());

        return Flowable
                .fromIterable(configurations)
                .collectInto(loggers, (map, configuration) ->
                    map.put(configuration.getName(), configuration.getData())
                );
    }

    /**
     * @param configuration The logger configuration
     * @return A {@link Single} that wraps the configuration data
     */
    protected static Single<Map<String, Object>> getLogger(
            LoggerConfiguration configuration) {
        return Single.just(configuration.getData());
    }

    /**
     * @return A list with all {@link LogLevel} values as strings
     */
    protected static Single<List<String>> getLogLevels() {
        return Flowable
                .fromArray(LogLevel.values())
                .map(LogLevel::name)
                .toList();
    }

    /**
     * @param level The log level as a String, or null
     * @return The {@link LogLevel} corresponding to the string, or NOT_SPECIFIED if string was null
     * @throws IllegalArgumentException if level is invalid (non-null, doesn't match any {@link LogLevel})
     */
    protected static LogLevel toLogLevel(String level) {
        if (level == null) {
            return LogLevel.NOT_SPECIFIED;
        }

        return LogLevel.valueOf(level);
    }

}
