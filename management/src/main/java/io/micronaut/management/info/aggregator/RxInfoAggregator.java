package io.micronaut.management.info.aggregator;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourcePropertyResolver;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.micronaut.management.info.source.InfoSource;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.*;
import java.util.stream.Collectors;

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
        Flowable<PropertySource> results = aggregateResults(sources);

        Single<Map<String, Object>> result = results.toList().map((List<PropertySource> list) -> {
            PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver();
            list.stream().sorted(OrderUtil.reverseComparator).forEach(resolver::addPropertySource);

            return resolver.getAllProperties();
        });


        return result.toFlowable();
    }


    //TODO: Does not handle ordering of info sources correctly
    protected Flowable<PropertySource> aggregateResults(InfoSource[] sources) {
        return Flowable.merge(
                Arrays.stream(sources)
                        .map(InfoSource::getSource)
                        .collect(Collectors.toList())
        );
    }
}
