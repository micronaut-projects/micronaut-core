package io.micronaut.management.endpoint.loggers.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.loggers.LoggerConfiguration;
import io.micronaut.management.endpoint.loggers.LoggersDataCollector;
import io.micronaut.management.endpoint.loggers.LoggersEndpoint;
import io.reactivex.Single;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

@Singleton
@Requires(beans = LoggersEndpoint.class)
public class RxLoggersDataCollector implements LoggersDataCollector<Map<String, Object>> {

    @Override
    public Publisher<Map<String, Object>> getData(Stream<LoggerConfiguration> loggerConfigurations) {
        return Single.just(Collections.<String, Object>emptyMap()).toFlowable();
    }

}
