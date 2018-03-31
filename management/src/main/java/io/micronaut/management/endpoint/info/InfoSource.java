package io.micronaut.management.endpoint.info;

import io.micronaut.context.env.PropertySource;
import io.micronaut.core.order.Ordered;
import org.reactivestreams.Publisher;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public interface InfoSource extends Ordered {

    Publisher<PropertySource> getSource();

}
