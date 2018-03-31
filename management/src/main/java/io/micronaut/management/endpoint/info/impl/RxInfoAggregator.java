package io.micronaut.management.endpoint.info.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourcePropertyResolver;
import io.micronaut.management.endpoint.info.InfoAggregator;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.micronaut.management.endpoint.info.InfoSource;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.*;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

/**
 * <p>Default implementation of {@link InfoAggregator}
 * @author Zachary Klein
 * @since 1.0
 */
@Singleton
@Requires(beans = InfoEndpoint.class)
public class RxInfoAggregator implements InfoAggregator<Map<String, Object>> {

    @Override
    public Publisher<Map<String, Object>> aggregate(InfoSource[] sources) {
        return aggregateResults(sources).toList().map((List<Map.Entry<Integer, PropertySource>> list) -> {
            PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver();
            list.stream()
                    .sorted((e1, e2) -> Integer.compare(e2.getKey(), e1.getKey()))
                    .forEach((entry) -> resolver.addPropertySource(entry.getValue()));
            return resolver.getAllProperties();
        }).toFlowable();
    }

    protected Flowable<Map.Entry<Integer, PropertySource>> aggregateResults(InfoSource[] sources) {
        List<Publisher<Map.Entry<Integer, PropertySource>>> publishers = new ArrayList<>(sources.length);
        for (int i = 0; i < sources.length; i++) {
            int index = i;
            Single<Map.Entry<Integer, PropertySource>> single = Flowable.fromPublisher(sources[i].getSource())
                    .firstOrError()
                    .map((source) -> new AbstractMap.SimpleEntry<>(index, source));
            publishers.add(single.toFlowable());
        }
        return Flowable.merge(publishers);
    }
}
