/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.reactive.rxjava2;

import io.micronaut.scheduling.instrument.Instrumentation;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import static java.util.Objects.requireNonNull;

/**
 * Wrapper publisher to instrument each {@link #subscribe(Subscriber)} invocation plus invocations of all
 * {@link Subscriber} methods with the given {@link ConditionalInstrumenter}.
 * <p/>
 * Example use-case: place key-value pair to MDC in http filters:
 * <p/>
 * <pre>
 * public Publisher&lt;MutableHttpResponse&lt;?&gt;&gt; doFilter(HttpRequest&lt;?&gt; request, ServerFilterChain chain) {
 *     ...
 *     ConditionalInstrumenter instrumenter = new MdcKeyValuePairInstrumenter("key", value);
 *     return new ConditionalInstrumentedPublisher<>(chain.proceed(request), instrumenter);
 * }
 * </pre>
 *
 * @param <T> the type of published elements
 * @author lgathy
 * @see ConditionalInstrumentedSubscriber
 * @since 2.0
 */
@SuppressWarnings("ReactiveStreamsPublisherImplementation")
public class ConditionalInstrumentedPublisher<T> implements Publisher<T> {

    private final Publisher<T> publisher;
    private final ConditionalInstrumenter instrumenter;

    /**
     * Default constructor.
     *
     * @param publisher    The source publisher
     * @param instrumenter The instrumenter
     */
    public ConditionalInstrumentedPublisher(Publisher<T> publisher, ConditionalInstrumenter instrumenter) {
        this.publisher = requireNonNull(publisher, "publisher");
        this.instrumenter = requireNonNull(instrumenter, "instrumenter");
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        try (Instrumentation ignored = instrumenter.newInstrumentation()) {
            publisher.subscribe(ConditionalInstrumentedSubscriber.wrap(subscriber, instrumenter));
        }
    }
}
