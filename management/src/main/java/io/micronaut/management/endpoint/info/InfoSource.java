package io.micronaut.management.endpoint.info;

import io.micronaut.context.env.PropertySource;
import io.micronaut.core.order.Ordered;
import org.reactivestreams.Publisher;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * <p>Describes an source of info that will be retrieved by the {@link InfoEndpoint}. </p>
 *
 * @author Zachary Klein
 * @since 1.0
 */
public interface InfoSource extends Ordered {


    /**
     * @return A publisher that returns a {@link PropertySource} containing
     * data to be added to the endpoint response
     */
    Publisher<PropertySource> getSource();

}
