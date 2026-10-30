package io.micronaut.aop.async;

import jakarta.inject.Singleton;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.concurrent.Flow;

@Singleton
@AsyncObserved
public class AsyncBean {

    public Flow.Publisher<String> flowPublisher() {
        return FlowAdapters.toFlowPublisher(Flux.just("a", "b"));
    }

    public Publisher<String> reactiveStreamsPublisher() {
        return Flux.just("a", "b");
    }

    public String string() {
        return "a";
    }
}
