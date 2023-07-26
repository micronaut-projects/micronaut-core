package io.micronaut.docs.propagation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.propagation.slf4j.MdcPropagationContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import org.reactivestreams.Publisher;
import org.slf4j.MDC;

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN;

@Requires(property = "mdc.example.legacy.filter.enabled")
// tag::class[]
@Filter(MATCH_ALL_PATTERN)
public class MdcLegacyFilter implements HttpServerFilter {

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request,
                                                      ServerFilterChain chain) {
        try {
            String trackingId = request.getHeaders().get("X-TrackingId");
            MDC.put("trackingId", trackingId);
            try (PropagatedContext.Scope ignore = PropagatedContext.get().plus(new MdcPropagationContext()).propagate()) {
                return chain.proceed(request);
            }
        } finally {
            MDC.remove("trackingId");
        }
    }

}
// end::class[]
