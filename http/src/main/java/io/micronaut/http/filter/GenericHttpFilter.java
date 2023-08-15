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

import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import org.reactivestreams.Publisher;

import java.util.function.Function;

/**
 * Base interface for different filter types. Note that while the base interface is exposed, so you
 * can pass around instances of these filters, the different implementations are internal only.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
public sealed interface GenericHttpFilter permits InternalHttpFilter {

    /**
     * When the filter is using the continuation it needs to be suspended and wait for the response.
     * @return true if suspended
     * @deprecated Not needed anymore
     */
    @Deprecated(forRemoval = true)
    default boolean isSuspended() {
        return false;
    }

    /**
     * @return true if the filter can receive the processing exception.
     * @deprecated Not needed anymore
     */
    @Deprecated(forRemoval = true)
    default boolean isFiltersException() {
        return false;
    }

    /**
     * Create a legacy filter.
     * @param bean The {@link HttpFilter} bean.
     * @param order The order
     * @return new filter
     * @since 4.2.0
     */
    static GenericHttpFilter createLegacyFilter(HttpFilter bean, FilterOrder order) {
        return new AroundLegacyFilter(bean, order);
    }

    /**
     * Create a terminal filter.
     * @param fn The function that supplier {@link MutableHttpResponse} from {@link HttpRequest}.
     * @return new terminal filter
     * @since 4.2.0
     */
    static GenericHttpFilter terminal(Function<HttpRequest<?>, ExecutionFlow<MutableHttpResponse<?>>> fn) {
        return new TerminalFilter(fn);
    }

    /**
     * Create a terminal reactive filter.
     * @param responsePublisher The response publisher
     * @return new terminal filter
     * @since 4.2.0
     */
    static GenericHttpFilter terminalReactive(Publisher<? extends HttpResponse<?>> responsePublisher) {
        return new TerminalReactiveFilter(responsePublisher);
    }

}
