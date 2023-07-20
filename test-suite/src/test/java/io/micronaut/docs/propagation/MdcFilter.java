package io.micronaut.docs.propagation;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import org.slf4j.MDC;

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN;

@Requires(property = "mdc.example.filter.enabled")
// tag::class[]
@ServerFilter(MATCH_ALL_PATTERN)
public class MdcFilter {

    @RequestFilter
    public void myRequestFilter(HttpRequest<?> request, MutablePropagatedContext mutablePropagatedContext) {
        try {
            String trackingId = request.getHeaders().get("X-TrackingId");
            MDC.put("trackingId", trackingId);
            mutablePropagatedContext.add(new MdcPropagationContext());
        } finally {
            MDC.remove("trackingId");
        }
    }

}
// end::class[]
