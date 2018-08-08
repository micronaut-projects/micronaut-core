package io.micronaut.tracing.instrument.util;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

@SuppressWarnings("PublisherImplementation")
class ScopePropagationPublisher<T> implements Publisher<T> {
    private final Publisher<T> publisher;
    private final Tracer tracer;
    private final Span parentSpan;

    public ScopePropagationPublisher(Publisher<T> publisher, Tracer tracer, Span parentSpan) {
        this.publisher = publisher;
        this.tracer = tracer;
        this.parentSpan = parentSpan;
    }

    @Override
    public void subscribe(Subscriber<? super T> actual) {
        Span span = parentSpan;
        if (span != null) {
            try (Scope ignored = tracer.scopeManager().activate(span, false)) {
                publisher.subscribe(new Subscriber<T>() {
                    boolean finished = false;
                    @Override
                    public void onSubscribe(Subscription s) {
                        try (Scope ignored = tracer.scopeManager().activate(span, false)) {
                            actual.onSubscribe(s);
                        }
                    }

                    @Override
                    public void onNext(T object) {
                        try (Scope ignored = tracer.scopeManager().activate(span, false)) {
                            actual.onNext(object);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        try (Scope ignored = tracer.scopeManager().activate(span, false)) {
                            actual.onError(t);
                        }
                    }

                    @Override
                    public void onComplete() {
                        try (Scope ignored = tracer.scopeManager().activate(span, false)) {
                            actual.onComplete();
                        }
                    }
                });
            }
        } else {
            publisher.subscribe(actual);
        }
    }
}
