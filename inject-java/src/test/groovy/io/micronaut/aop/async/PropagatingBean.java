package io.micronaut.aop.async;

import io.micronaut.core.async.propagation.ReactorPropagation;
import jakarta.inject.Singleton;
import org.reactivestreams.FlowAdapters;
import reactor.core.publisher.Flux;

import java.util.concurrent.Flow;

@Singleton
@Propagating
public class PropagatingBean {

    public Flow.Publisher<String> flowPublisher() {
        return FlowAdapters.toFlowPublisher(marker());
    }

    public Flux<String> flux() {
        return marker();
    }

    private static Flux<String> marker() {
        return Flux.deferContextual(ctx -> Flux.just(
            ReactorPropagation.findContextElement(ctx, PropagatingInterceptor.Marker.class)
                .map(PropagatingInterceptor.Marker::value)
                .orElse("missing")
        ));
    }
}
