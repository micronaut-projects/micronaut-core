package io.micronaut.configuration.metrics.binder.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import org.reactivestreams.Publisher;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;

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
@Requires(beans = MeterRegistry.class)
@Requires(property = MICRONAUT_METRICS_ENABLED, value = "true", defaultValue = "true")
@Requires(property = MICRONAUT_METRICS + "binders.web.enabled", value = "true", defaultValue = "true")
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
