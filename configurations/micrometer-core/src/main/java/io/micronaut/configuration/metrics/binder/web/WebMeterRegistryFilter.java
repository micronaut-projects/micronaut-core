/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.metrics.binder.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import org.reactivestreams.Publisher;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;

/**
 * Once per request web filter that will register the timers
 * and meters for each request.
 * <p>
 * The defualt is to intercept all paths /**, but using the
 * property micronaut.metrics.http.path, this can be changed.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
@Filter("${micronaut.metrics.http.path:/**}")
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".web.enabled", value = "true", defaultValue = "true")
public class WebMeterRegistryFilter extends OncePerRequestHttpServerFilter {

    private final MeterRegistry meterRegistry;

    /**
     * Filter constructor.
     *
     * @param meterRegistry the meter registry
     */
    public WebMeterRegistryFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * The method that will be invoked once per request.
     *
     * @param httpRequest the http request
     * @param chain       The {@link ServerFilterChain} instance
     * @return a publisher with the response
     */
    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> httpRequest, ServerFilterChain chain) {
        long start = System.nanoTime();
        Publisher<MutableHttpResponse<?>> responsePublisher = chain.proceed(httpRequest);
        return new MetricsPublisher(responsePublisher, meterRegistry, httpRequest.getPath(), start, httpRequest.getMethod().toString());
    }
}
