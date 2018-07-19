package io.micronaut.management.endpoint.loggers.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.loggers.*;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.*;

@Singleton
@Requires(beans = LoggersEndpoint.class)
public class RxLoggersDataCollector implements LoggersDataCollector<Map<String, Object>> {

    @Override
    public Publisher<Map<String, Object>> getData(LoggingSystem loggingSystem) {
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
    public Publisher<Map<String, Object>> getOne(LoggingSystem loggingSystem, String name) {
        return Single.just(loggingSystem.getLogger(name).getData())
                .toFlowable();
    }

    /**
     * @param configurations The logger configurations
     * @return A {@link Single} that wraps a Map
     */
    protected Single<Map<String, Object>> getLoggers(Collection<LoggerConfiguration> configurations) {
        Map<String, Object> loggers = new HashMap<>(configurations.size());

        return Flowable
                .fromIterable(configurations)
                .collectInto(loggers, (map, configuration) ->
                    map.put(configuration.getName(), configuration.getData())
                );
    }

    /**
     * @return A list with all {@link LogLevel} values as strings
     */
    protected Single<List<String>> getLogLevels() {
        return Flowable
                .fromArray(LogLevel.values())
                .map(LogLevel::name)
                .toList();
    }

}
