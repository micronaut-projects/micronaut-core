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
package io.micronaut.http.server.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.scheduling.instrument.Instrumentation;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;
import io.micronaut.scheduling.instrument.InvocationInstrumenterFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.ArrayList;
import java.util.List;

/**
 * A filter that instruments the request with the {@link io.micronaut.http.context.ServerRequestContext}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Filter(Filter.MATCH_ALL_PATTERN)
@Internal
public final class ServerRequestContextFilter implements HttpServerFilter {

    private final List<InvocationInstrumenterFactory> invocationInstrumenterFactories;

    /**
     * Creates new filter.
     *
     * @param invocationInstrumenterFactories the invocationInstrumenterFactories
     */
    public ServerRequestContextFilter(List<InvocationInstrumenterFactory> invocationInstrumenterFactories) {
        this.invocationInstrumenterFactories = invocationInstrumenterFactories;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        try (Instrumentation instrumentation = InvocationInstrumenter.combine(getInvocationInstrumenter(request)).newInstrumentation()) {
            Publisher<MutableHttpResponse<?>> actual = chain.proceed(request);
            InvocationInstrumenter invocationInstrumenterAfterProceed
                    = InvocationInstrumenter.combine(getInvocationInstrumenter(request));
            return new Publisher<MutableHttpResponse<?>>() {
                @Override
                public void subscribe(Subscriber<? super MutableHttpResponse<?>> actualSubscriber) {
                    try (Instrumentation ignored = invocationInstrumenterAfterProceed.newInstrumentation()) {
                        actual.subscribe(actualSubscriber);
                    }
                }
            };
        }
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.FIRST.order();
    }

    private List<InvocationInstrumenter> getInvocationInstrumenter(HttpRequest<?> request) {
        List<InvocationInstrumenter> instrumenters = new ArrayList<>(invocationInstrumenterFactories.size() + 1);
        instrumenters.add(new ServerRequestContextInvocationInstrumenter(request));
        for (InvocationInstrumenterFactory instrumenterFactory : invocationInstrumenterFactories) {
            final InvocationInstrumenter instrumenter = instrumenterFactory.newInvocationInstrumenter();
            if (instrumenter != null) {
                instrumenters.add(instrumenter);
            }
        }
        return instrumenters;
    }

}
