package io.micronaut.core.async.propagation;

import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

import java.util.concurrent.TimeUnit;

/**
 * The per-signal cost of {@link ReactivePropagation}: a streamed body, where every element passes
 * through the propagating subscriber. Scores are per element.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class ReactivePropagationBenchmark {

    static final int N = 100;

    PropagatedContext context;
    Flux<Integer> source;

    record Element(int id) implements PropagatedContextElement {
    }

    @Setup
    public void setUp() {
        context = PropagatedContext.empty().plus(new Element(1));
        source = Flux.range(0, N);
    }

    /**
     * Demand comes from outside the context, one element at a time, like a response writer
     * requesting the next element of a streamed body: every element binds the context.
     */
    @Benchmark
    @OperationsPerInvocation(N)
    public void demandOutsideContext(Blackhole bh) {
        Sink sink = new Sink(bh, false);
        ReactivePropagation.propagate(context, source).subscribe(sink);
        for (int i = 0; i < N; i++) {
            sink.subscription.request(1);
        }
    }

    /**
     * All demand requested on subscribe, inside the context: every element finds the context
     * bound.
     */
    @Benchmark
    @OperationsPerInvocation(N)
    public void demandInsideContext(Blackhole bh) {
        Sink sink = new Sink(bh, true);
        ReactivePropagation.propagate(context, source).subscribe(sink);
    }

    /**
     * Operators read the subscriber context, for example a contextWrite or deferContextual
     * upstream.
     */
    @Benchmark
    @OperationsPerInvocation(N)
    public void currentContext(Blackhole bh) {
        CoreSubscriber<Integer> subscriber = (CoreSubscriber<Integer>) ReactivePropagation.propagate(context, new Sink(bh, true));
        for (int i = 0; i < N; i++) {
            bh.consume(subscriber.currentContext());
        }
    }

    static final class Sink implements CoreSubscriber<Integer> {
        final Blackhole bh;
        final boolean requestAll;
        Subscription subscription;

        Sink(Blackhole bh, boolean requestAll) {
            this.bh = bh;
            this.requestAll = requestAll;
        }

        @Override
        public Context currentContext() {
            return Context.empty();
        }

        @Override
        public void onSubscribe(Subscription s) {
            subscription = s;
            if (requestAll) {
                s.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onNext(Integer integer) {
            bh.consume(integer);
        }

        @Override
        public void onError(Throwable t) {
            bh.consume(t);
        }

        @Override
        public void onComplete() {
            bh.consume(this);
        }
    }
}
