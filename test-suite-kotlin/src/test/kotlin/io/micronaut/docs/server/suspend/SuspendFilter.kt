package io.micronaut.docs.server.suspend

import io.micronaut.http.HttpAttributes
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.OncePerRequestHttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.reactivex.Flowable
import org.reactivestreams.Publisher

@Filter("/suspend/illegalWithContext")
class SuspendFilter : OncePerRequestHttpServerFilter() {

    lateinit var response: MutableHttpResponse<*>
    var error: Throwable? = null

    override fun doFilterOnce(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> {
        return Flowable.fromPublisher(chain.proceed(request)).doOnNext { rsp ->
                    response = rsp
                    error = rsp.getAttribute(HttpAttributes.EXCEPTION, Throwable::class.java).orElse(null)
                }
    }
}
