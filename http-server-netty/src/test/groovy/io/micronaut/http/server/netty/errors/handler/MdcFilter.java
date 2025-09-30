package io.micronaut.http.server.netty.errors.handler;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.propagation.slf4j.MdcPropagationContext;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import org.slf4j.MDC;

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN;

@Requires(property = "spec.name", value = "PropagatedContextExceptionHandlerTest")
@ServerFilter(MATCH_ALL_PATTERN)
public class MdcFilter {

    @RequestFilter
    public void requestFilter(MutablePropagatedContext mutablePropagatedContext) {

        String traceId = "1234";
        MDC.put("trace", traceId);

        mutablePropagatedContext.add(new MdcPropagationContext());
    }

    @ResponseFilter
    public void addTraceIdHeader(MutableHttpResponse<?> response) {
        MDC.remove("trace");
    }
}
