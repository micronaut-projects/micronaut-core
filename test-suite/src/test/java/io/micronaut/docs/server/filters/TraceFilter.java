package io.micronaut.docs.server.filters;

// tag::imports[]
import io.micronaut.http.*;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.*;
import org.reactivestreams.Publisher;
// end::imports[]


// tag::class[]
@Filter("/hello/**") // <1>
public class TraceFilter implements HttpServerFilter { // <2>
    private final TraceService traceService;

    public TraceFilter(TraceService traceService) { // <3>
        this.traceService = traceService;
    }
// end::class[]

    // tag::doFilter[]
    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        return traceService.trace(request) // <1>
                           .switchMap(aBoolean -> chain.proceed(request)) // <2>
                           .doOnNext(res -> // <3>
                                res.getHeaders().add("X-Trace-Enabled", "true")
                           );
    }
    // end::doFilter[]

// tag::endclass[]
}
// end::endclass[]
