/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.tracing.instrument.util;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * A {@link Publisher} that just propagates tracing state without creating a new span.
 *
 * @author graemerocher
 * @since 1.0
 * @param <T> The publisher generic type
 */
@SuppressWarnings("PublisherImplementation")
public class ScopePropagationPublisher<T> implements Publisher<T> {
    private final Publisher<T> publisher;
    private final Tracer tracer;
    private final Span parentSpan;

    /**
     * The default constructor.
     *
     * @param publisher The publisher
     * @param tracer The tracer
     * @param parentSpan The parent span
     */
    public ScopePropagationPublisher(Publisher<T> publisher, Tracer tracer, Span parentSpan) {
        this.publisher = publisher;
        this.tracer = tracer;
        this.parentSpan = parentSpan;
    }

    @SuppressWarnings("SubscriberImplementation")
    @Override
    public void subscribe(Subscriber<? super T> actual) {
        Span span = parentSpan;
        if (span != null) {
            try (Scope ignored = tracer.scopeManager().activate(span)) {
                publisher.subscribe(new Subscriber<T>() {
                    @Override
                    public void onSubscribe(Subscription s) {
                        try (Scope ignored = tracer.scopeManager().activate(span)) {
                            actual.onSubscribe(s);
                        }
                    }

                    @Override
                    public void onNext(T object) {
                        try (Scope ignored = tracer.scopeManager().activate(span)) {
                            actual.onNext(object);
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        try (Scope ignored = tracer.scopeManager().activate(span)) {
                            actual.onError(t);
                        }
                    }

                    @Override
                    public void onComplete() {
                        try (Scope ignored = tracer.scopeManager().activate(span)) {
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
