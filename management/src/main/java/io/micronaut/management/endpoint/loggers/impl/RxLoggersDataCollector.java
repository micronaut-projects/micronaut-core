package io.micronaut.management.endpoint.loggers.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.loggers.LogLevel;
import io.micronaut.management.endpoint.loggers.LoggerConfiguration;
import io.micronaut.management.endpoint.loggers.LoggersDataCollector;
import io.micronaut.management.endpoint.loggers.LoggersEndpoint;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@Requires(beans = LoggersEndpoint.class)
public class RxLoggersDataCollector implements LoggersDataCollector<Map<String, Object>> {

    @Override
    public Publisher<Map<String, Object>> getData(Collection<LoggerConfiguration> configurations) {
        return Single.zip(getLoggers(configurations), getLogLevels(),
                (loggers, levels) -> {
                    Map<String, Object> data = new HashMap<>(2);
                    data.put("loggers", loggers);
                    data.put("levels", levels);
                    return data;
                })
                .toFlowable();
    }

    /**
     * @param configurations The logger configurations
     * @return A {@link Single} that wraps a Map
     */
    protected Single<Map<String, Object>> getLoggers(Collection<LoggerConfiguration> configurations) {
        Map<String, Object> loggers = new ConcurrentHashMap<>(configurations.size());

        return Flowable
                .fromIterable(configurations)
                .collectInto(loggers, (map, configuration) ->
                        map.put(configuration.getName(), configuration)
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
