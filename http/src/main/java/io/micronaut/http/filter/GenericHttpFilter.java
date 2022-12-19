/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import org.reactivestreams.Publisher;
import reactor.util.context.Context;

import java.util.concurrent.Executor;

/**
 * Base interface for different filter types. Note that while the base interface is exposed, so you
 * can pass around instances of these filters, the different implementations are internal only.
 * Only the framework should construct or call instances of this interface. The exception is the
 * {@link Terminal terminal filter}.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
public sealed interface GenericHttpFilter
    permits
    FilterRunner.FilterMethod,
    GenericHttpFilter.AroundLegacy,
    GenericHttpFilter.Async,
    GenericHttpFilter.Terminal,
    GenericHttpFilter.TerminalReactive,
    GenericHttpFilter.TerminalWithReactorContext {
    /**
     * Wrapper around a filter that signifies the filter should be run asynchronously on the given
     * executor. Usually from an {@link io.micronaut.scheduling.annotation.ExecuteOn} annotation.
     *
     * @param actual Actual filter
     * @param executor Executor to run the filter on
     */
    @Internal
    record Async(
        GenericHttpFilter actual,
        Executor executor
    ) implements GenericHttpFilter, Ordered {
        @Override
        public int getOrder() {
            return ((Ordered) actual).getOrder();
        }
    }

    /**
     * "Legacy" filter, i.e. filter bean that implements {@link HttpFilter}.
     *
     * @param bean The filter bean
     * @param order The filter order
     */
    @Internal
    record AroundLegacy(
        HttpFilter bean,
        FilterOrder order
    ) implements GenericHttpFilter, Ordered {
        public boolean isEnabled() {
            return !(bean instanceof Toggleable t) || t.isEnabled();
        }

        @Override
        public int getOrder() {
            return order.getOrder(bean);
        }
    }

    /**
     * Terminal filter that accepts a reactive type. Used as a temporary solution for the http
     * client, until that is un-reactified.
     *
     * @param responsePublisher The response publisher
     */
    @Internal
    record TerminalReactive(Publisher<? extends HttpResponse<?>> responsePublisher) implements GenericHttpFilter {
    }

    /**
     * Like {@link Terminal}, with an additional parameter for the reactive context.
     */
    @Internal
    @FunctionalInterface
    non-sealed interface TerminalWithReactorContext extends GenericHttpFilter {
        ExecutionFlow<? extends HttpResponse<?>> execute(HttpRequest<?> request, Context context) throws Exception;
    }

    /**
     * Last item in a filter chain, called when all other filters are done. Basically, this runs
     * the actual request.
     */
    @FunctionalInterface
    non-sealed interface Terminal extends GenericHttpFilter {
        ExecutionFlow<? extends HttpResponse<?>> execute(HttpRequest<?> request) throws Exception;
    }
}
