package io.micronaut.management.endpoint.info;

import io.micronaut.management.endpoint.Endpoint;
import org.reactivestreams.Publisher;

/**
 * <p>Aggregates all registered info sources into a single response.</p>
 *
 * @author Zachary Klein
 * @since 1.0
 */
@Endpoint("info")
public interface InfoAggregator<T> {

    Publisher<T> aggregate(InfoSource[] sources);
}
