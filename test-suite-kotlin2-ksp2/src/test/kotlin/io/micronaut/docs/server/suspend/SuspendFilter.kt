package io.micronaut.docs.server.suspend

import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.web.router.RouteAttributes
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

@Filter("/suspend/illegalWithContext")
class SuspendFilter : HttpServerFilter {

    lateinit var response: MutableHttpResponse<*>
    var error: Throwable? = null

    override fun doFilter(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> {
        return Flux.from(chain.proceed(request)).doOnNext { rsp ->
                    response = rsp
                    error = RouteAttributes.getException(rsp).orElse(null)
                }
    }
}
