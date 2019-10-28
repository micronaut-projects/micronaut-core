package io.micronaut.docs.annotation.requestattributes

import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import org.reactivestreams.Publisher

import java.util.HashMap

@Filter("/story/**")
class StoryClientFilter : HttpClientFilter {

    private var attributes: Map<String, Any>? = null

    /**
     * strictly for unit testing
     */
    internal val latestRequestAttributes: Map<String, Any>
        get() = HashMap(attributes!!)

    override fun doFilter(request: MutableHttpRequest<*>, chain: ClientFilterChain): Publisher<out HttpResponse<*>> {
        attributes = request.attributes.asMap()
        return chain.proceed(request)
    }
}
