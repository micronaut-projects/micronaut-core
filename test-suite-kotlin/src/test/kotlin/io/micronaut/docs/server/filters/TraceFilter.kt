package io.micronaut.docs.server.filters

// tag::imports[]

import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import org.reactivestreams.Publisher

// end::imports[]

// tag::class[]
@Filter("/hello/**") // <1>
class TraceFilter(// <2>
        private val traceService: TraceService)// <3>
    : HttpServerFilter {
    // end::class[]

    // tag::doFilter[]
    override fun doFilter(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> {
        return traceService.trace(request) // <1>
                .switchMap { aBoolean -> chain.proceed(request) } // <2>
                .doOnNext { res ->
                    // <3>
                    res.headers.add("X-Trace-Enabled", "true")
                }
    }
    // end::doFilter[]

// tag::endclass[]
}
// end::endclass[]
